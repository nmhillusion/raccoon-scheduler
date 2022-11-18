package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PoliticianEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.HttpHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.RegexHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.util.DateUtil;
import app.netlify.nmhillusion.raccoon_scheduler.validator.StringValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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

    @Autowired
    private RegexHelper regexHelper;

    @Override
    public void execute() throws Exception {
        getLog(this).info("running for rulers");

        final List<IndexEntity> indexLinks = parseHomePage();
        getLog(this).info("parser index links: " + indexLinks);

        if (!indexLinks.isEmpty()) {
            final List<PoliticianEntity> politicianEntities = parseCharacterPage(indexLinks.get(0));

            LogHelper.getLog(this).info("politician list -> " + politicianEntities.size());
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
//        getLog(this).info("pageContent: " + pageContent);

        final List<List<String>> parsedList = regexHelper.parse(pageContent, "<a\\s+href=['\"](index\\w\\d*.html)['\"]>([\\w-]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        parsedList.forEach(parsed -> {
            indexLinks.add(new IndexEntity()
                    .setHref(String.valueOf(parsed.get(1)).trim())
                    .setTitle(String.valueOf(parsed.get(2)).trim())
            );
        });

        return indexLinks;
    }

    private LocalDate parseDateOfBirthPhrase(String phrase) {
        LocalDate localDate = null;

        final List<List<String>> parsedList = regexHelper.parse(phrase, "\\bb\\.(\\s*[a-z]{3,6})\\.?\\s*(\\d+),\\s*(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
        if (!parsedList.isEmpty()) {
            final List<String> parsed = parsedList.get(0);
            final Month month = DateUtil.convertMonthFromShortNameOfMonth(String.valueOf(parsed.get(1)).trim());
            final int day = Integer.parseInt(String.valueOf(parsed.get(2)).trim());
            final int year = Integer.parseInt(String.valueOf(parsed.get(3)).trim());

            localDate = LocalDate.of(year, month, day);
        }

        return localDate;
    }

    private LocalDate parseDateOfDeathPhrase(String phrase) {
        LocalDate localDate = null;

        final List<List<String>> parsedList = regexHelper.parse(phrase, "\\bd\\.(\\s*[a-z]{3,6})\\.?\\s*(\\d+),\\s*(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
        if (!parsedList.isEmpty()) {
            final List<String> parsed = parsedList.get(0);
            final Month month = DateUtil.convertMonthFromShortNameOfMonth(String.valueOf(parsed.get(1)).trim());
            final int day = Integer.parseInt(String.valueOf(parsed.get(2)).trim());
            final int year = Integer.parseInt(String.valueOf(parsed.get(3)).trim());

            localDate = LocalDate.of(year, month, day);
        }

        return localDate;
    }

    private Optional<PoliticianEntity> parseCharacterParagraph(String paragraph) {
        final List<List<String>> parsedList = regexHelper.parse(paragraph, "(.+?)</b>\\s*\\((.*?)\\),(.*?)\\.(.+?)\\.<p>.*?", Pattern.CASE_INSENSITIVE);

        Optional<PoliticianEntity> result = Optional.empty();

        final Optional<List<String>> firstParsed = parsedList.stream().findFirst();
        if (firstParsed.isPresent()) {
            String fullName = String.valueOf(firstParsed.get().get(1)).trim();
            String lifeTime = String.valueOf(firstParsed.get().get(2)).trim();
            String role = String.valueOf(firstParsed.get().get(3)).trim();
            String note = String.valueOf(firstParsed.get().get(4)).trim();

            result = Optional.of(new PoliticianEntity()
                    .setFullName(fullName)
                    .setDateOfBirth(parseDateOfBirthPhrase(lifeTime))
                    .setDateOfDeath(parseDateOfDeathPhrase(lifeTime))
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

//        getLog(this).info("page content of character: " + pageContent);

        if (StringValidator.isBlank(pageContent)) {
            throw new IOException("Page Content is empty");
        }

        final String[] splitCharacter = pageContent.split("(?i)<b>");

        getLog(this).info("splitCharacter: " + splitCharacter.length);

        if (1 >= splitCharacter.length) {
            throw new Exception("Structure of character page is not valid");
        }

        final List<String> characterParagraphs = Arrays.asList(splitCharacter).subList(1, splitCharacter.length);

        for (String characterParagraph : characterParagraphs) {
            final Optional<PoliticianEntity> politicianEntity = parseCharacterParagraph(characterParagraph);
            politicianEntity.ifPresent(resultList::add);
        }

        return resultList;
    }
}
