package tech.nmhillusion.raccoon_scheduler.service_impl.euro_cup_stat;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.nmhillusion.n2mix.exception.GeneralException;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.type.ChainMap;
import tech.nmhillusion.n2mix.util.StringUtil;
import tech.nmhillusion.n2mix.validator.StringValidator;
import tech.nmhillusion.raccoon_scheduler.entity.world_cup_stat.MatchStatEntity;
import tech.nmhillusion.raccoon_scheduler.service.CrawlEuroCupStatsService;
import tech.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

@Service
public class CrawlEuroCupStatsServiceImpl extends BaseSchedulerServiceImpl implements CrawlEuroCupStatsService {
    private static final String FS_COLLECTION_ID = "raccoon-scheduler--euro-cup-stat";
    private final HttpHelper httpHelper = new HttpHelper();
    private final List<String> ID_EURO_CUP_STAT_ELS = new ArrayList<>();
    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();
    private final MatchParser matchParser = new MatchParser();
    private String EURO_CUP_STATS_PAGE_URL = "";
    @Value("${service.crawl-euro-cup-stats.enable}")
    private boolean enableExecution;

    public CrawlEuroCupStatsServiceImpl() {
        init();
    }

    private String getConfig(String key) {
        try {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("service-config/euro-cup-stats.yml")) {
                return new YamlReader(is).getProperty(key, String.class, "");
            }
        } catch (Exception ex) {
            getLogger(this).error(ex);
            return "";
        }
    }

    private void init() {
        EURO_CUP_STATS_PAGE_URL = getConfig("pageUrl");
        ID_EURO_CUP_STAT_ELS.addAll(Arrays.stream(StringUtil.trimWithNull(getConfig("domElement.idEuroCupStatEl")).split(",")).map(StringUtil::trimWithNull).toList());
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
            final String homeTeamShortName = matchParser.parseHomeTeamShortNameFromCells(cells);
            final String score = matchParser.parseScoreFromCells(cells);
            final String awayTeam = matchParser.parseAwayTeamFromCells(cells);
            final String awayTeamShortName = matchParser.parseAwayTeamShortNameFromCells(cells);
            final int attendance = matchParser.parseAttendanceFromCells(cells);
            final String venue = matchParser.parseVenueFromCells(cells);
            final String referee = matchParser.parseRefereeFromCells(cells);
            final int homeTeamScore = matchParser.parseHomeTeamScoreFromScore(score);
            final int awayTeamScore = matchParser.parseAwayTeamScoreFromScore(score);
            final int homeTeamPenaltyScore = matchParser.parseHomeTeamPenaltyScoreFromScore(score);
            final int awayTeamPenaltyScore = matchParser.parseAwayTeamPenaltyScoreFromScore(score);

            getLogger(this).infoFormat("match: " + new ChainMap<String, Object>()
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
                    .chainPut("homeTeamShortName", homeTeamShortName)
                    .chainPut("awayTeamShortName", awayTeamShortName)
                    .chainPut("homeTeamPenaltyScore", homeTeamPenaltyScore)
                    .chainPut("awayTeamPenaltyScore", awayTeamPenaltyScore)
            );

            if (!StringValidator.isBlank(homeTeam) && !StringValidator.isBlank(awayTeam)) {
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
                        .setVenue(venue)
                        .setHomeTeamShortName(homeTeamShortName)
                        .setAwayTeamShortName(awayTeamShortName)
                        .setHomeTeamPenaltyScore(homeTeamPenaltyScore)
                        .setAwayTeamPenaltyScore(awayTeamPenaltyScore);
                statList.add(
                        matchEntity
                                .setMatchId(generateKeyForMatch(matchEntity))
                );
            } else {
                getLogger(this).warn("empty of home team and away team in match content: " + matchContent);
            }
        }

        return statList;
    }

    private String trailSpaceWithUnderscore(String name) {
        return StringUtil.trimWithNull(name).replaceAll("\\s", "_");
    }

    private String generateKeyForMatch(MatchStatEntity matchEntity) {
        if (null == matchEntity.getStartTime()) {
            getLogger(this).info("null start time: " + matchEntity);
        }
        return trailSpaceWithUnderscore(matchEntity.getHomeTeam()) + "__" + trailSpaceWithUnderscore(matchEntity.getAwayTeam()) + "__" + matchEntity.getStartTime().toEpochSecond();
    }


    @Override
    public void doExecute() throws Throwable {
        final String statPageContent = new String(httpHelper.get(new RequestHttpBuilder()
                .setUrl(EURO_CUP_STATS_PAGE_URL)));

        final List<MatchStatEntity> statEntityList = buildMatchListFromStatPageContent(statPageContent);
        getLogger(this).info("match --> " + statEntityList);
        getLogger(this).info("matches length: " + statEntityList.size());
        updateWorldCupStatToFirestore(statEntityList);
    }

    @Override
    public List<MatchStatEntity> buildMatchListFromStatPageContent(String statPageContent) throws GeneralException {
        final List<String> obtainedMainStatContentList = matchParser.obtainMainStat(statPageContent, ID_EURO_CUP_STAT_ELS);
        final List<String> matchContentList = matchParser.obtainForEachMatch(obtainedMainStatContentList);

        getLogger(this).debug("obtainedMainStatContentList: " + obtainedMainStatContentList);

        if (matchContentList.isEmpty()) {
            throw new GeneralException("Match content list is empty");
        }

        return buildMatchListFromMatchContent(matchContentList);
    }

    private void updateWorldCupStatToFirestore(List<MatchStatEntity> statEntityList) throws Throwable {
        firebaseWrapper
                .runWithWrapper(firebaseHelper -> {
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
                });
    }
}
