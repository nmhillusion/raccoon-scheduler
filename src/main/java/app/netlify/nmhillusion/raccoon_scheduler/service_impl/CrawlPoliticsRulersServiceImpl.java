package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.PoliticsRulersIndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.HttpHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

@Service
public class CrawlPoliticsRulersServiceImpl implements CrawlPoliticsRulersService {

    private static final String MAIN_RULERS_PAGE_URL = "https://rulers.org/";
    @Autowired
    private HttpHelper httpHelper;

    @Override
    public void execute() throws Exception {
        getLog(this).info("running for rulers");

        final List<PoliticsRulersIndexEntity> indexLinks = parseHomePage();
        getLog(this).info("parser index links: " + indexLinks);

        if (!indexLinks.isEmpty()) {
            parseCharacterPage(indexLinks.get(0));
        }
    }

    private List<PoliticsRulersIndexEntity> parseHomePage() throws IOException {
        final List<PoliticsRulersIndexEntity> indexLinks = new ArrayList<>();

        // TODO: testing data
//        final String pageContent = new String(httpHelper.get(MAIN_RULERS_PAGE_URL));
//        getLog(this).info("pageContent: " + pageContent);

        String pageContent = "";

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/home-page.html")) {
            getLog(this).debug("loaded stream --> " + inputStream);
        }

        final Pattern indexPattern = Pattern.compile("<a\\s+href=['\"](index\\w\\d*.html)['\"]>([\\w-]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        final Matcher matcher = indexPattern.matcher(pageContent);

        getLog(this).info("matched: " + matcher.groupCount());

        while (matcher.find()) {
            getLog(this).debug("Full match: " + matcher.group(0));

            for (int matchIdx = 1; matchIdx <= matcher.groupCount(); matchIdx++) {
                getLog(this).debug("Group " + matchIdx + ": " + matcher.group(matchIdx));
            }

            indexLinks.add(new PoliticsRulersIndexEntity()
                    .setHref(matcher.group(1))
                    .setTitle(matcher.group(2))
            );
        }

        return indexLinks;
    }

    private void parseCharacterPage(PoliticsRulersIndexEntity indexEntity) {
        getLog(this).info("do parseCharacterPage --> " + indexEntity);
    }
}
