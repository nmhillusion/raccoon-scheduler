package app.netlify.nmhillusion.raccoon_scheduler.service_impl.world_cup_stat;

import app.netlify.nmhillusion.n2mix.exception.GeneralException;
import app.netlify.nmhillusion.n2mix.helper.YamlReader;
import app.netlify.nmhillusion.n2mix.helper.firebase.FirebaseHelper;
import app.netlify.nmhillusion.n2mix.helper.http.HttpHelper;
import app.netlify.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import app.netlify.nmhillusion.n2mix.type.ChainMap;
import app.netlify.nmhillusion.n2mix.util.IOStreamUtil;
import app.netlify.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import app.netlify.nmhillusion.raccoon_scheduler.entity.world_cup_stat.MatchStatEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlWorldCupStatsService;
import app.netlify.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

@Service
public class CrawlWorldCupStatsServiceImpl extends BaseSchedulerServiceImpl implements CrawlWorldCupStatsService {
    private static final String FS_COLLECTION_ID = "raccoon-scheduler--world-cup-stat";
    private final HttpHelper httpHelper = new HttpHelper();
    private String WORLD_CUP_STATS_PAGE_URL = "";
    private String ID_WORLD_CUP_STAT_EL = "";

    @Autowired
    private MatchParser matchParser;

    @Value("${service.crawl-world-cup-stats.enable}")
    private boolean enableExecution;

    @Value("${service.crawl-world-cup-stats.testing}")
    private boolean isTesting;

    private String getConfig(String key) {
        try {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("service-config/world-cup-stats.yml")) {
                return new YamlReader(is).getProperty(key, String.class, "");
            }
        } catch (Exception ex) {
            getLog(this).error(ex);
            return "";
        }
    }

    @PostConstruct
    private void init() {
        WORLD_CUP_STATS_PAGE_URL = getConfig("pageUrl");
        ID_WORLD_CUP_STAT_EL = getConfig("domElement.idWorldCupStatEl");
    }

    @Override
    public boolean isEnableExecution() {
        return enableExecution;
    }

    private List<MatchStatEntity> buildMatchListFromMatchContent(List<String> matchContentList) {
        final List<MatchStatEntity> statList = new ArrayList<>();

        for (String matchContent : matchContentList) {
            final List<String> cells = matchParser.parseEachRowToCellList(matchContent);

            final String mDate = matchParser.parseDateFromCells(cells);
            final ZonedDateTime startTime = matchParser.parseStartTimeFromCells(cells);
            final String homeTeam = matchParser.parseHomeTeamFromCells(cells);
            final String score = matchParser.parseScoreFromCells(cells);
            final String awayTeam = matchParser.parseAwayTeamFromCells(cells);
            final int attendance = matchParser.parseAttendanceFromCells(cells);
            final String venue = matchParser.parseVenueFromCells(cells);
            final String referee = matchParser.parseRefereeFromCells(cells);
            final int homeTeamScore = matchParser.parseHomeTeamScoreFromScore(score);
            final int awayTeamScore = matchParser.parseAwayTeamScoreFromScore(score);
            getLog(this).infoFormat("match: " + new ChainMap<String, Object>()
                    .chainPut("mDate", mDate)
                    .chainPut("startTime", startTime)
                    .chainPut("homeTeam", homeTeam)
                    .chainPut("score", score)
                    .chainPut("awayTeam", awayTeam)
                    .chainPut("attendance", attendance)
                    .chainPut("venue", venue)
                    .chainPut("referee", referee)
                    .chainPut("homeTeamScore", homeTeamScore)
                    .chainPut("awayTeamScore", awayTeamScore)
            );

            final MatchStatEntity matchEntity = new MatchStatEntity()
                    .setAttendance(attendance)
                    .setAwayTeam(awayTeam)
                    .setAwayTeamScore(awayTeamScore)
                    .setDate(mDate)
                    .setHomeTeam(homeTeam)
                    .setHomeTeamScore(homeTeamScore)
                    .setReferee(referee)
                    .setScore(score)
                    .setStartTime(startTime)
                    .setVenue(venue);
            statList.add(
                    matchEntity
                            .setMatchId(generateKeyForMatch(matchEntity))
            );
        }

        return statList;
    }

    private String generateKeyForMatch(MatchStatEntity matchEntity) {
        return matchEntity.getHomeTeam() + "__" + matchEntity.getAwayTeam() + "__" + matchEntity.getStartTime().toEpochSecond();
    }


    @Override
    public void doExecute() throws Exception {
        String statPageContent = "";

        if (!isTesting) {
            statPageContent = new String(httpHelper.get(new RequestHttpBuilder()
                    .setUrl(WORLD_CUP_STATS_PAGE_URL)));
        } else {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("test-data/world-cup-stat/stat.html")) {
                if (null != is) {
                    statPageContent = IOStreamUtil.convertInputStreamToString(is);
                }
            }
        }

        final String obtainedMainStatContent = matchParser.obtainMainStat(statPageContent, ID_WORLD_CUP_STAT_EL);
        final List<String> matchContentList = matchParser.obtainForEachMatch(obtainedMainStatContent);

        if (matchContentList.isEmpty()) {
            throw new GeneralException("Match content list is empty");
        }

        final List<MatchStatEntity> statEntityList = buildMatchListFromMatchContent(matchContentList);
        getLog(this).info("match --> " + statEntityList);
        getLog(this).info("matches length: " + statEntityList.size());
        updateWorldCupStatToFirestore(statEntityList);
    }

    private void updateWorldCupStatToFirestore(List<MatchStatEntity> statEntityList) throws GeneralException, IOException {
        try (FirebaseHelper firebaseHelper = new FirebaseHelper(FirebaseConfigConstant.getInstance().getFirebaseConfig())) {
            final Optional<Firestore> firestoreOpt = firebaseHelper.getFirestore();
            if (firestoreOpt.isEmpty()) {
                throw new GeneralException("Cannot obtain Firestore");
            }

            final DocumentReference statRef = firestoreOpt.get().collection(FS_COLLECTION_ID).document("stat");
            statRef.set(new ChainMap<>()
                    .chainPut("updated", ZonedDateTime.now().toString())
                    .chainPut("data",
                            statEntityList.stream().map(MatchStatEntity::toMap).collect(Collectors.toList())
                    )
            );
        }
    }
}