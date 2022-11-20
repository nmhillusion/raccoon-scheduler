package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.n2mix.helper.HttpHelper;
import app.netlify.nmhillusion.n2mix.util.DateUtil;
import app.netlify.nmhillusion.n2mix.util.RegexUtil;
import app.netlify.nmhillusion.n2mix.util.StringUtil;
import app.netlify.nmhillusion.n2mix.validator.StringValidator;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PoliticianEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

@Service
public class CrawlPoliticsRulersServiceImpl implements CrawlPoliticsRulersService {

    private static final String MAIN_RULERS_PAGE_URL = "https://rulers.org/";

    private final HttpHelper httpHelper = new HttpHelper();

    @Override
    public void execute() throws Exception {
        getLog(this).info("running for rulers");

        final Map<String, List<PoliticianEntity>> politicianData = new HashMap<>();
        final List<IndexEntity> indexLinks = parseHomePage();
        getLog(this).info("parser index links: " + indexLinks);

        if (!indexLinks.isEmpty()) {
            for (IndexEntity indexLinkItem : indexLinks) {
                final List<PoliticianEntity> politicianEntities = parseCharacterPage(indexLinkItem);
                getLog(this).info("politician list -> " + politicianEntities.size());

                politicianData.put(indexLinkItem.getTitle(), politicianEntities);

                break; /// Mark: TEST
            }
        }

        getLog(this).info("All politician list: " + politicianData);
    }

    private List<IndexEntity> parseHomePage() throws IOException {
        final List<IndexEntity> indexLinks = new ArrayList<>();

        /// Mark: TEST (start)
//        final String pageContent = new String(httpHelper.get(MAIN_RULERS_PAGE_URL));
        String pageContent = "";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/home-page.html")) {
            getLog(this).debug("loaded stream --> " + inputStream);
            pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        /// Mark: TEST (end)

        getLog(this).info("pageContent: " + pageContent);

        final List<List<String>> parsedList = RegexUtil.parse(pageContent, "<a\\s+href=['\"](index\\w\\d*.html)['\"]>([\\w-]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        parsedList.forEach(parsed -> {
            indexLinks.add(new IndexEntity()
                    .setHref(StringUtil.trimWithNull(parsed.get(1)))
                    .setTitle(StringUtil.trimWithNull(parsed.get(2)))
            );
        });

        return indexLinks;
    }

    private String buildDatePatternOfPrefix(String prefix) {
        return "\\b" + prefix + "\\.\\s*(?:([a-z]{3,6})\\.?)?\\s*(?:(\\d+),)?\\s*(\\d{4})\\b";
    }

    private LocalDate parseDateOfBirthPhrase(String phrase) {
        LocalDate localDate = null;

        final List<List<String>> parsedList = RegexUtil.parse(phrase, buildDatePatternOfPrefix("b"), Pattern.CASE_INSENSITIVE);
        if (!parsedList.isEmpty()) {
            final List<String> parsed = parsedList.get(0);
            localDate = DateUtil.buildDateFromString(parsed.get(2), parsed.get(1), parsed.get(3));
        }

        return localDate;
    }

    private LocalDate parseDateOfDeathPhrase(String phrase) {
        LocalDate localDate = null;

        final List<List<String>> parsedList = RegexUtil.parse(phrase, buildDatePatternOfPrefix("d"), Pattern.CASE_INSENSITIVE);
        if (!parsedList.isEmpty()) {
            final List<String> parsed = parsedList.get(0);
            localDate = DateUtil.buildDateFromString(parsed.get(2), parsed.get(1), parsed.get(3));
        }

        return localDate;
    }

    private Optional<PoliticianEntity> parseCharacterParagraph(String paragraph) {
        paragraph = StringUtil.trimWithNull(paragraph);
        final List<List<String>> parsedList = RegexUtil.parse(paragraph, "(.+?)<\\/b>\\s*\\((.*?)\\),(.*?)\\.(?:(.+?)\\.)?<p>.*?", Pattern.CASE_INSENSITIVE);

        Optional<PoliticianEntity> result = Optional.empty();

        final Optional<List<String>> firstParsed = parsedList.stream().findFirst();
        if (firstParsed.isPresent()) {
            final String fullName = HtmlUtils.htmlUnescape(
                    StringUtil.trimWithNull(
                            firstParsed.get().get(1)
                    )
            );
            final String lifeTime = HtmlUtils.htmlUnescape(
                    StringUtil.trimWithNull(
                            firstParsed.get().get(2)
                    )
            );
            final String role = HtmlUtils.htmlUnescape(
                    StringUtil.trimWithNull(
                            firstParsed.get().get(3)
                    )
            );
            final String note = HtmlUtils.htmlUnescape(
                    StringUtil.trimWithNull(
                            firstParsed.get().get(4)
                    )
            );

            result = Optional.of(new PoliticianEntity()
                    .setFullName(fullName)
                    .setDateOfBirth(parseDateOfBirthPhrase(lifeTime))
                    .setDateOfDeath(parseDateOfDeathPhrase(lifeTime))
                    .setRole(StringUtil.removeHtmlTag(role))
                    .setNote(StringUtil.removeHtmlTag(note))
            );
        }

        return result;
    }

    private String getCharacterPageUrl(String characterPagePartLink) {
        final int lastIndexOfSlash = MAIN_RULERS_PAGE_URL.lastIndexOf("/");
        String baseLink = MAIN_RULERS_PAGE_URL;
        if (-1 < lastIndexOfSlash) {
            baseLink = MAIN_RULERS_PAGE_URL.substring(0, lastIndexOfSlash);
        }

        return (baseLink + "/" + characterPagePartLink).replace("//", "/");
    }

    private List<PoliticianEntity> parseCharacterPage(IndexEntity indexEntity) throws Exception {
        final List<PoliticianEntity> resultList = new ArrayList<>();

        getLog(this).info("do parseCharacterPage --> " + indexEntity);

        /// Mark: TEST (start)
//        final String pageContent = new String(httpHelper.get(getCharacterPageUrl(indexEntity.getHref())));
        String pageContent = "";
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/character-page-content.html")) {
            pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
        /// Mark: TEST (end)

        getLog(this).info("[" + indexEntity.getTitle() + "] page content of character: " + pageContent);

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
