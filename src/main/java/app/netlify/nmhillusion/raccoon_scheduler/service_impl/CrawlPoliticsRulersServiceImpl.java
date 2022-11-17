package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PoliticianEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.HttpHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.validator.StringValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        final List<IndexEntity> indexLinks = parseHomePage();
        getLog(this).info("parser index links: " + indexLinks);

        if (!indexLinks.isEmpty()) {
            parseCharacterPage(indexLinks.get(0));
        }
    }

    private List<IndexEntity> parseHomePage() throws IOException {
        final List<IndexEntity> indexLinks = new ArrayList<>();

        // TODO: testing data
//        final String pageContent = new String(httpHelper.get(MAIN_RULERS_PAGE_URL));
        String pageContent = "";

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/home-page.html")) {
            getLog(this).debug("loaded stream --> " + inputStream);
            pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        getLog(this).info("pageContent: " + pageContent);

        final Pattern indexPattern = Pattern.compile("<a\\s+href=['\"](index\\w\\d*.html)['\"]>([\\w-]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        final Matcher matcher = indexPattern.matcher(pageContent);

        getLog(this).info("matched: " + matcher.groupCount());

        while (matcher.find()) {
            getLog(this).debug("Full match: " + matcher.group(0));

            for (int matchIdx = 1; matchIdx <= matcher.groupCount(); matchIdx++) {
                getLog(this).debug("Group " + matchIdx + ": " + matcher.group(matchIdx));
            }

            indexLinks.add(new IndexEntity()
                    .setHref(matcher.group(1))
                    .setTitle(matcher.group(2))
            );
        }

        return indexLinks;
    }

    private Optional<PoliticianEntity> parseCharacterParagraph(String paragraph) {
        final Pattern pattern_ = Pattern.compile("(.+?)</b>\\s*(\\(.*?\\)),(.*?)\\.(.+?)\\..*?", Pattern.CASE_INSENSITIVE);
        final Matcher matcher = pattern_.matcher(paragraph);

        Optional<PoliticianEntity> result = Optional.empty();

        if (matcher.find()) {
            String fullName = matcher.group(1);
            String lifeTime = matcher.group(2);
            String role = matcher.group(3);
            String note = matcher.group(4);

            result = Optional.of(new PoliticianEntity()
                    .setFullName(fullName)
                    .setDateOfBirth(lifeTime)
                    .setDateOfDeath(lifeTime)
                    .setRole(role)
                    .setNote(note)
            );
        }

        return result;
    }

    private List<PoliticianEntity> parseCharacterPage(IndexEntity indexEntity) throws Exception {
        final List<PoliticianEntity> resultList = new ArrayList<>();

        getLog(this).info("do parseCharacterPage --> " + indexEntity);

        String pageContent = "";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/character-page-content.html")) {
            pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }

        getLog(this).info("page content of character: " + pageContent);

        if (StringValidator.isBlank(pageContent)) {
            throw new IOException("Page Content is empty");
        }

        final String[] splitCharacter = pageContent.split("(?i)<b>");

        getLog(this).info("splitCharacter: " + splitCharacter.length);

        if (1 >= splitCharacter.length) {
            throw new Exception("Structure of character page is not valid");
        }

        final List<String> characterParagraphs = Arrays.asList(splitCharacter).subList(1, splitCharacter.length);

        getLog(this).info("first character: " + characterParagraphs.get(0));

        final Optional<PoliticianEntity> politicianEntity = parseCharacterParagraph(characterParagraphs.get(0));

        LogHelper.getLog(this).info("parse politician: " + politicianEntity);

        return resultList;
    }
}
