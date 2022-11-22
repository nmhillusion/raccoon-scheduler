package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.n2mix.helper.http.HttpHelper;
import app.netlify.nmhillusion.n2mix.helper.office.ExcelWriteHelper;
import app.netlify.nmhillusion.n2mix.helper.office.excel.ExcelDataModel;
import app.netlify.nmhillusion.n2mix.util.DateUtil;
import app.netlify.nmhillusion.n2mix.util.RegexUtil;
import app.netlify.nmhillusion.n2mix.util.StringUtil;
import app.netlify.nmhillusion.n2mix.validator.StringValidator;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PoliticianEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.service.GmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    @Autowired
    private GmailService gmailService;

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
        exportToExcel(politicianData);
    }

    private List<String> buildExcelDataFromPolitician(PoliticianEntity politician) {
        return Arrays.asList(
                politician.getOriginalParagraph(),
                politician.getFullName(),
                StringUtil.trimWithNull(politician.getDateOfBirth()),
                politician.getPlaceOfBirth(),
                StringUtil.trimWithNull(politician.getDateOfDeath()),
                politician.getPlaceOfDeath(),
                politician.getRole(),
                politician.getNote()
        );
    }

    private void exportToExcel(Map<String, List<PoliticianEntity>> politicianData) throws IOException {
        final ExcelWriteHelper excelWriteHelper = new ExcelWriteHelper();
        politicianData.forEach((key, data) -> {
            excelWriteHelper.addSheetData(new ExcelDataModel()
                    .setSheetName(key)
                    .setHeaders(Collections.singletonList(Arrays.asList("origin", "name", "dateOfBirth", "placeOfBirth", "dateOfDeath", "placeOfDeath", "role", "note")))
                    .setBodyData(data.stream().map(this::buildExcelDataFromPolitician).collect(Collectors.toList()))
            );
        });

        final byte[] outData = excelWriteHelper.build();
        try (OutputStream os = new FileOutputStream("test-politicians.xlsx")) {
            os.write(outData);
            os.flush();
        }
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
        return prefix + "\\.\\s*(?:([a-z]{3,6})\\.?)?\\s*(?:(\\d+),)?\\s*(\\d{4})";
    }

    private String buildPatternOfPlaceOfBirth() {
        return buildDatePatternOfPrefix("b") + "(.*?)\s*-\s*" + buildDatePatternOfPrefix("d");
    }

    private String buildPatternOfPlaceOfDeath() {
        return buildDatePatternOfPrefix("b") + "(?:.*?)\s*-\s*" + buildDatePatternOfPrefix("d") + "(.+?)$";
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

    private String parsePlaceOfLifetime(String lifetime, boolean isBirth) {
        String place = "";

        final List<List<String>> parsedList = RegexUtil.parse(lifetime, isBirth ? buildPatternOfPlaceOfBirth() : buildPatternOfPlaceOfDeath(), Pattern.CASE_INSENSITIVE);
        if (!parsedList.isEmpty()) {
            final List<String> parsed = parsedList.get(0);
            place = StringUtil.trimWithNull(isBirth ? parsed.get(4) : parsed.get(7));

            while (place.matches("^\\W(.+?)") && 1 < place.length()) {
                place = StringUtil.trimWithNull(place.substring(1));
            }
            place = StringUtil.trimWithNull(place);

            while (place.matches("(.+?)\\W$") && 1 < place.length()) {
                place = StringUtil.trimWithNull(place.substring(0, place.length() - 1));
            }
            place = StringUtil.trimWithNull(place);
        }

        return place;
    }

    private String parsePlaceOfBirth(String lifetime) {
        return parsePlaceOfLifetime(lifetime, true);
    }

    private String parsePlaceOfDeath(String lifetime) {
        return parsePlaceOfLifetime(lifetime, false);
    }

    private Optional<PoliticianEntity> parseCharacterParagraph(String paragraph) {
        paragraph = StringUtil.trimWithNull(paragraph);
        final List<List<String>> parsedList = RegexUtil.parse(paragraph, "(.+?)<\\/b>\\s*\\((.*?)\\),(.*?)\\.(?:(.+?)\\.)?<p>.*?", Pattern.CASE_INSENSITIVE);

        Optional<PoliticianEntity> result = Optional.empty();

        final Optional<List<String>> firstParsed = parsedList.stream().findFirst();
        if (firstParsed.isPresent()) {
            final String originalParagraph = HtmlUtils.htmlUnescape(
                    StringUtil.trimWithNull(
                            firstParsed.get().get(0)
                    )
            );

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
                    .setOriginalParagraph(originalParagraph)
                    .setFullName(fullName)
                    .setDateOfBirth(parseDateOfBirthPhrase(lifeTime))
                    .setDateOfDeath(parseDateOfDeathPhrase(lifeTime))
                    .setPlaceOfBirth(StringUtil.removeHtmlTag(parsePlaceOfBirth(lifeTime)))
                    .setPlaceOfDeath(StringUtil.removeHtmlTag(parsePlaceOfDeath(lifeTime)))
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
