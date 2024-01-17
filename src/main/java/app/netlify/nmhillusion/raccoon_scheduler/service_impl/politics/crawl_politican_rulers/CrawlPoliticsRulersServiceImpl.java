package app.netlify.nmhillusion.raccoon_scheduler.service_impl.politics.crawl_politican_rulers;

import app.netlify.nmhillusion.raccoon_scheduler.entity.gmail.AttachmentEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.gmail.MailEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.gmail.SendEmailResponse;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers.IndexEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers.PendingUserEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers.PoliticianEntity;
import app.netlify.nmhillusion.raccoon_scheduler.service.GmailService;
import app.netlify.nmhillusion.raccoon_scheduler.service.politics.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import tech.nmhillusion.n2mix.constant.ContentType;
import tech.nmhillusion.n2mix.exception.GeneralException;
import tech.nmhillusion.n2mix.exception.MissingDataException;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.helper.office.excel.writer.ExcelWriteHelper;
import tech.nmhillusion.n2mix.helper.office.excel.writer.model.BasicExcelDataModel;
import tech.nmhillusion.n2mix.util.CollectionUtil;
import tech.nmhillusion.n2mix.util.ExceptionUtil;
import tech.nmhillusion.n2mix.util.RegexUtil;
import tech.nmhillusion.n2mix.util.StringUtil;
import tech.nmhillusion.n2mix.validator.StringValidator;

import javax.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

@Service
public class CrawlPoliticsRulersServiceImpl extends BaseSchedulerServiceImpl implements CrawlPoliticsRulersService {

    private static final String MAIN_RULERS_PAGE_URL = "https://rulers.org/";
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 10_000;
    private static final Charset RULERS_CHARSET = StandardCharsets.ISO_8859_1;

    private final HttpHelper httpHelper = new HttpHelper();
    private final MatchParser matchParser = new MatchParser();

    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();

    @Autowired
    private GmailService gmailService;
    private YamlReader yamlReader;
    @Value("${service.politics.crawl-politics-rulers.enable}")
    private boolean enableExecution;

    @Value("${service.politics.crawl-politics-rulers.testing}")
    private boolean isTesting;
    private DateTimeFormatter exportDataDateTimeFormatter;

    private synchronized String getConfig(String key) {
        try {
            if (null == yamlReader) {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("service-config/politics/politics-rulers.yml")) {
                    yamlReader = new YamlReader(is);
                }
            }

            return yamlReader.getProperty(key, String.class, null);
        } catch (Exception ex) {
            getLogger(this).error(ex);
            return "";
        }
    }

    @PostConstruct
    private void init() {
        this.exportDataDateTimeFormatter = new DateTimeFormatterBuilder()
                .appendPattern(getConfig("export.excel.date-format"))
                .toFormatter();
    }

    private String buildMailFromTemplate(PendingUserEntity pendingUser) {
        try {
            final String mailTemplate = getConfig("mail.template");

            return mailTemplate.replace("{{user_fullName}}", pendingUser.getFullName());
        } catch (Exception ex) {
            getLogger(this).error(ex);
            return "";
        }
    }

    @Override
    public boolean isEnableExecution() {
        return enableExecution;
    }

    @Override
    public void doExecute() throws Exception {
        if (CollectionUtil.isNullOrEmpty(getPendingUsers())) {
            getLogger(this).warn("Do not run because of empty pending users");
            return;
        }

        getLogger(this).info("Running for fetching politicians from Rulers");

        final Map<String, List<PoliticianEntity>> politicianData = new HashMap<>();
        final List<IndexEntity> indexLinks = parseHomePage();
        getLogger(this).info("parser index links: " + indexLinks);

        if (!indexLinks.isEmpty()) {
            for (IndexEntity indexLinkItem : indexLinks) {
                final long startTime = System.currentTimeMillis();

                final List<PoliticianEntity> politicianEntities = fetchCharacterPage(indexLinkItem);
                getLogger(this).info("politician list -> " + politicianEntities.size());

                politicianData.put(indexLinkItem.getTitle(), politicianEntities);
                if (isTesting) {
                    break; /// Mark: TESTING
                }

                while (MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS > System.currentTimeMillis() - startTime) ;
            }
        }

        getLogger(this).info("All politician list SIZE: " + politicianData.size());
        final Map<String, byte[]> excelData = exportToExcel(politicianData);

        if (!isTesting) {
            final List<PendingUserEntity> pendingUsers = getPendingUsers();
            getLogger(this).info("pending users: " + pendingUsers);

            if (!CollectionUtil.isNullOrEmpty(pendingUsers)) {
                doSendMailToPendingUsers(excelData, pendingUsers);
                cleanPendingUser();
            }
        } else {
            for (String chainName : excelData.keySet()) {
                try (OutputStream os = new FileOutputStream(chainName + ".test.xlsx")) {
                    os.write(excelData.get(chainName));
                    os.flush();
                }
            }

//            final List<PendingUserEntity> pendingUsers = getPendingUsers();
//            getLog(this).info("pending users: " + pendingUsers);
//
//            if (!CollectionUtil.isNullOrEmpty(pendingUsers)) {
//                doSendMailToPendingUsers(excelData, pendingUsers);
//                cleanPendingUser();
//            }
        }
    }

    private void doSendMailToPendingUsers(Map<String, byte[]> excelData, List<PendingUserEntity> pendingUsers) throws Exception {
        final String mailSubject = getConfig("mail.subject");
        final List<String> ccMails = Arrays.asList(getConfig("mail.cc").split(","));
        final List<AttachmentEntity> attachments = excelData.keySet().stream().map(characterKey ->
                {
                    final String base64DataOfCharacterExcel = new String(Base64.getEncoder().encode(excelData.get(characterKey)));

                    return new AttachmentEntity()
                            .setName(characterKey + "__" + getConfig("mail.attachment.name"))
                            .setContentType(ContentType.MS_EXCEL_XLSX)
                            .setBase64Data(base64DataOfCharacterExcel);
                }
        ).collect(Collectors.toList());

        for (PendingUserEntity pendingUser : pendingUsers) {
            if (!StringValidator.isBlank(pendingUser.getEmail())) {
                final SendEmailResponse sendMailResult = gmailService.sendMail(new MailEntity()
                        .setSubject(mailSubject)
                        .setRecipientMails(Collections.singletonList(pendingUser.getEmail()))
                        .setCcMails(ccMails)
                        .setHtmlBody(buildMailFromTemplate(pendingUser))
                        .setAttachments(attachments)
                );

                getLogger(this).info(pendingUser + " |> result send mail to pending user: " + sendMailResult);

                if (!sendMailResult.getSuccess()) {
                    throw new GeneralException("Fail to send result of Politician Rulers to user because of " + sendMailResult);
                }
            } else {
                getLogger(this).warn("Do not send mail because not existing email of pending user: " + pendingUser);
            }
        }
    }

    @Override
    public List<PendingUserEntity> getPendingUsers() {
        try {
            final List<PendingUserEntity> userList = new ArrayList<>();

            firebaseWrapper
                    .runWithWrapper(firebaseHelper -> {
                        final Optional<Firestore> firestoreOptional = firebaseHelper.getFirestore();

                        if (firestoreOptional.isPresent()) {
                            final Firestore firestore_ = firestoreOptional.get();
                            final ApiFuture<DocumentSnapshot> pendingUsersSnap = firestore_.document("raccoon-scheduler--politician-rulers/pending-users").get();
                            final Map<String, Object> dataCollection = pendingUsersSnap.get().getData();

                            if (null != dataCollection) {
                                final Object data_ = dataCollection.get("data");
                                if (data_ instanceof List<?> dataList) {
                                    for (Object item : dataList) {
                                        if (item instanceof Map<?, ?> itemMap) {
                                            userList.add(
                                                    new PendingUserEntity()
                                                            .setEmail(StringUtil.trimWithNull(itemMap.get("email")))
                                                            .setFullName(StringUtil.trimWithNull(itemMap.get("fullName")))
                                            );
                                        }
                                    }
                                }
                            }
                        }
                    });
            return userList;
        } catch (Throwable ex) {
            getLogger(this).error(ex);
            throw ExceptionUtil.throwException(ex);
        }
    }

    private void cleanPendingUser() {
        try {
            firebaseWrapper
                    .runWithWrapper(firebaseHelper -> {
                        final Optional<Firestore> firestoreOptional = firebaseHelper.getFirestore();

                        final List<PendingUserEntity> userList = new ArrayList<>();

                        if (firestoreOptional.isPresent()) {
                            final Firestore firestore_ = firestoreOptional.get();
                            final DocumentReference pendingUserRef = firestore_.document("raccoon-scheduler--politician-rulers/pending-users");
                            pendingUserRef.update("data", new ArrayList<>());
                        }
                    });
        } catch (Throwable ex) {
            getLogger(this).error(ex);
            throw ExceptionUtil.throwException(ex);
        }
    }

    private String formatDateWhenExportToExcel(LocalDate dataDate) {
        try {
            if (null == dataDate) {
                return StringUtil.EMPTY;
            }
            return StringUtil.trimWithNull(
                    exportDataDateTimeFormatter.format(dataDate)
            );
        } catch (Exception ex) {
            getLogger(this).error(ex);
            return StringUtil.EMPTY;
        }
    }

    private List<String> buildExcelDataFromPolitician(PoliticianEntity politician) {
        return Arrays.asList(
                politician.getOriginalParagraph(),
                politician.getFullName(),
                politician.getPrimaryName(),
                politician.getSecondaryName(),
                formatDateWhenExportToExcel(politician.getDateOfBirth()),
                politician.getPlaceOfBirth(),
                formatDateWhenExportToExcel(politician.getDateOfDeath()),
                politician.getPlaceOfDeath(),
                politician.getPosition(),
                politician.getNote()
        );
    }

    private Map<String, byte[]> exportToExcel(Map<String, List<PoliticianEntity>> politicianData) throws IOException {
        final Map<String, byte[]> exportData = new HashMap<>();
        politicianData.forEach((key, data) -> {
            try {
                final byte[] itemExcelData = new ExcelWriteHelper()
                        .addSheetData(new BasicExcelDataModel()
                                .setSheetName(key)
                                .setHeaders(Collections.singletonList(Arrays.asList("origin", "fullName", "primaryName", "secondaryName", "dateOfBirth", "placeOfBirth", "dateOfDeath", "placeOfDeath", "role", "note")))
                                .setBodyData(data.stream().map(this::buildExcelDataFromPolitician).collect(Collectors.toList()))
                        ).build();

                exportData.put(key, itemExcelData);
            } catch (Exception ex) {
                getLogger(this).error(ex);
                try {
                    exportData.put(key, new ExcelWriteHelper().addSheetData(new BasicExcelDataModel()
                                    .setSheetName(key)
                                    .setHeaders(List.of(List.of("Error")))
                                    .setBodyData(List.of(Collections.singletonList(ex.getMessage())))
                            ).build()
                    );
                } catch (IOException | MissingDataException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return exportData;
    }

    private List<IndexEntity> parseHomePage() throws Exception {
        final List<IndexEntity> indexLinks = new ArrayList<>();

        /// Mark: TESTING (start)
        String pageContent = "";
        if (!isTesting) {
            pageContent = new String(httpHelper.get(
                    new RequestHttpBuilder()
                            .setUrl(MAIN_RULERS_PAGE_URL)
            ), RULERS_CHARSET);
        } else {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/home-page.html")) {
                getLogger(this).debug("loaded stream --> " + inputStream);
                pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        }
        /// Mark: TESTING (end)

        getLogger(this).info("pageContent: " + pageContent);

        final List<List<String>> parsedList = RegexUtil.parse(pageContent, "<a\\s+href=['\"](index\\w\\d*.html)['\"]>([\\w-]+)</a>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

        parsedList.forEach(parsed -> {
            indexLinks.add(new IndexEntity()
                    .setHref(StringUtil.trimWithNull(parsed.get(1)))
                    .setTitle(StringUtil.trimWithNull(parsed.get(2)))
            );
        });

        return indexLinks;
    }

    private String getCharacterPageUrl(String characterPagePartLink) {
        final int lastIndexOfSlash = MAIN_RULERS_PAGE_URL.lastIndexOf("/");
        String baseLink = MAIN_RULERS_PAGE_URL;
        if (-1 < lastIndexOfSlash) {
            baseLink = MAIN_RULERS_PAGE_URL.substring(0, lastIndexOfSlash);
        }

        return (baseLink + "/" + characterPagePartLink).replace("//", "/");
    }

    private List<PoliticianEntity> fetchCharacterPage(IndexEntity indexEntity) throws Exception {
        getLogger(this).info("do parseCharacterPage --> " + indexEntity);
        getLogger(this).debug("[" + indexEntity.getTitle() + "] page content of character: " + indexEntity.getHref());

        /// Mark: TESTING (start)
        String pageContent = "";
        if (!isTesting) {
            pageContent = new String(httpHelper.get(
                    new RequestHttpBuilder()
                            .setUrl(getCharacterPageUrl(indexEntity.getHref()))
            ), RULERS_CHARSET);
        } else {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("test-data/politics-rulers/character-page-content.html")) {
                pageContent = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        }
        /// Mark: TESTING (end)

        return matchParser.parseCharacterPage(pageContent);
    }
}
