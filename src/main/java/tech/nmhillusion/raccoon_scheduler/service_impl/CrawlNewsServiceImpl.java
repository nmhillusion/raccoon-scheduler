package tech.nmhillusion.raccoon_scheduler.service_impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import jakarta.annotation.PostConstruct;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.type.ChainMap;
import tech.nmhillusion.n2mix.type.Pair;
import tech.nmhillusion.n2mix.util.CollectionUtil;
import tech.nmhillusion.n2mix.util.DateUtil;
import tech.nmhillusion.n2mix.util.IOStreamUtil;
import tech.nmhillusion.n2mix.util.StringUtil;
import tech.nmhillusion.raccoon_scheduler.entity.gmail.MailEntity;
import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;
import tech.nmhillusion.raccoon_scheduler.helper.CrawlErrorCollector;
import tech.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import tech.nmhillusion.raccoon_scheduler.helper.NewsItemDocumentMapper;
import tech.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import tech.nmhillusion.raccoon_scheduler.service.GmailService;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;


/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */
@Service
public class CrawlNewsServiceImpl extends BaseSchedulerServiceImpl implements CrawlNewsService {
    //    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private static final String FIRESTORE_ITEMS_COLLECTION_PATH = "raccoon-scheduler--news--items";
    private static final String FIRESTORE_NEWS_SUMMARY_PATH = "raccoon-scheduler--news--summary";
    private static final String FIRESTORE_NEWS_SUMMARY_DOC_ID = "current";
    private static final int NEWS_RETENTION_HOURS = 24;
    private static final int CLEAN_UP_BATCH_SIZE = 500;
    private static final int EXISTING_ITEMS_READ_BATCH_SIZE = 100;
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private static final String LOCAL_FOLDER_PATH = "temp-data";
    private final List<String> DISABLED_SOURCES = new ArrayList<>();
    private final Map<String, Pattern> FILTERED_WORD_PATTERNS = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicInteger completedCrawlNewsSourceCount = new AtomicInteger();
    private final CrawlErrorCollector crawlErrorCollector = new CrawlErrorCollector();
    private final HttpHelper httpHelper = new HttpHelper();
    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();
    private final GmailService gmailService;
    @Value("${format.date-time}")
    private String dateTimeFormat;
    private DateTimeFormatter dateTimeFormatter;
    @Value("${service.crawl-news.enable}")
    private boolean enableExecution;
    private String updatedDateOfNewsSource;

    public CrawlNewsServiceImpl(GmailService gmailService) {
        this.gmailService = gmailService;
    }


    @PostConstruct
    private void init() {
        try (final InputStream crawlNewsStream = getClass().getClassLoader().getResourceAsStream("service-config/crawl-news.yml")) {
            final YamlReader yamlReader = new YamlReader(crawlNewsStream);

            dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormat);

            final String rawDisabledSources = yamlReader.getProperty("source-news.disabled-sources", String.class);
            DISABLED_SOURCES.addAll(Arrays.stream(rawDisabledSources.split(",")).map(String::trim).filter(
                    Predicate.not(String::isBlank)
            ).toList());

            final String rawFilteredWords = yamlReader.getProperty("source-news.filter-words", String.class);
            final String[] filteredWordArray = rawFilteredWords.split(",");
            Arrays.stream(filteredWordArray)
                    .map(String::trim)
                    .filter(Predicate.not(String::isBlank))
                    .map(word -> new ChainMap<String, Pattern>().chainPut(word, Pattern.compile("(^|\\W)" + word + "(\\W|$)", Pattern.CASE_INSENSITIVE)))
                    .forEach(FILTERED_WORD_PATTERNS::putAll);

            updatedDateOfNewsSource = yamlReader.getProperty("source-news.updatedTime", String.class);
            getLogger(this).info("updatedDateOfNewsSource = " + updatedDateOfNewsSource);
            getLogger(this).info("DISABLED_SOURCES: " + DISABLED_SOURCES);
            getLogger(this).info("rawFilteredWords: " + rawFilteredWords + "; FILTERED_WORDS: " + FILTERED_WORD_PATTERNS + "; size: " + FILTERED_WORD_PATTERNS.size());
        } catch (Exception ex) {
            getLogger(this).error(ex);
        }
    }


    @Override
    public boolean isEnableExecution() {
        return enableExecution;
    }

    private void updateForNewsSourceState(List<String> newsSourceList) throws Throwable {
        firebaseWrapper
                .runWithWrapper(firebaseHelper ->
                {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    if (_firestoreOpt.isPresent()) {
                        final Firestore firestore_ = _firestoreOpt.get();

                        final DocumentReference summaryDocRef = firestore_.collection(FIRESTORE_NEWS_SUMMARY_PATH)
                                .document(FIRESTORE_NEWS_SUMMARY_DOC_ID);
                        final ApiFuture<DocumentSnapshot> documentSnapshotApiFuture = summaryDocRef.get();
                        final DocumentSnapshot currentData = documentSnapshotApiFuture.get();
                        final String fbLastModifiedTime = currentData.get("lastModifiedTime", String.class);

                        if (!String.valueOf(updatedDateOfNewsSource).equals(fbLastModifiedTime)) {
                            final ApiFuture<WriteResult> pushStateFuture = summaryDocRef.set(
                                    new ChainMap<String, Object>()
                                            .chainPut("lastModifiedTime", updatedDateOfNewsSource)
                                            .chainPut("sourceKeys", newsSourceList),
                                    SetOptions.merge()
                            );

                            final WriteResult pushResult = pushStateFuture.get();

                            getLogger(this).info("updateForNewsSourceState - push result: " + pushResult);
                        }
                    }
                });
    }

    @Override
    public void doExecute() throws Throwable {
        final String newsSourcesFilename = "data/news-sources.json";

        try (final InputStream newsSourceStream = getClass().getClassLoader().getResourceAsStream(newsSourcesFilename)) {
            final JSONObject newsSourcesJsonConfig = new JSONObject(StreamUtils.copyToString(newsSourceStream, StandardCharsets.UTF_8));
            getLogger(this).info("Start crawl news from web >>");

            emptyLocalFolder();
            completedCrawlNewsSourceCount.setOpaque(0);
            crawlErrorCollector.clear();

            final List<String> newsSourceKeys = new ArrayList<>();
            {
                //-- Mark: ACTUAL...
                newsSourceKeys.addAll(
                        newsSourcesJsonConfig.keySet().stream()
                                .filter(Predicate.not(DISABLED_SOURCES::contains))
                                .toList()
                );
            }

//            {
//                //-- Mark: TESTING...
//            newsSourceKeys.add(
//                    "vnexpress"
//            );
//            }


            Collections.shuffle(newsSourceKeys);
            updateForNewsSourceState(newsSourceKeys);

            getLogger(this).info("==> newsSourceKeys: %s".formatted(newsSourceKeys));
//            final List<String> newsSourceKeys = List.of("voa-tieng-viet"); //-- Mark: TESTING

            final int newsSourceSize = newsSourceKeys.size();
            for (int sourceKeyIdx = 0; sourceKeyIdx < newsSourceSize; ++sourceKeyIdx) {
                final String sourceKey = newsSourceKeys.get(sourceKeyIdx);

                try {
                    final int tSourceKeyIndex = sourceKeyIdx;
                    executorService.submit(() -> {
                        try {
                            final Map<String, Pair<List<NewsEntity>, List<Exception>>> crawlInSourceNewsData = crawlInSourceNews(
                                    newsSourcesJsonConfig,
                                    sourceKey,
                                    tSourceKeyIndex,
                                    newsSourceSize
                            );
                            final List<NewsEntity> downloadedNewsFeedList = crawlInSourceNewsData
                                    .values()
                                    .stream()
                                    .flatMap(it -> it.getKey().stream())
                                    .filter(this::isValidFilteredNews)
                                    .toList();

                            if (!downloadedNewsFeedList.isEmpty()) {
                                saveToLocalFile(sourceKey, downloadedNewsFeedList);
                            }

                            final List<String> exceptionList = crawlInSourceNewsData.keySet()
                                    .stream()
                                    .filter(newsLink -> !crawlInSourceNewsData.get(newsLink)
                                            .getValue().isEmpty()
                                    )
                                    .map(newsLink -> {
                                                final Exception newsEx = crawlInSourceNewsData.get(newsLink)
                                                        .getValue()
                                                        .getFirst();

                                                return MessageFormat.format(
                                                        "Error on link: {0} - Exception: {1} - {2}"
                                                        , newsLink
                                                        , newsEx.getClass().getName()
                                                        , newsEx.getMessage()
                                                );
                                            }
                                    )
                                    .toList();

                            if (!exceptionList.isEmpty()) {
                                throw new Exception(
                                        String.join("\n", exceptionList)
                                );
                            }
                        } catch (Throwable ex) {
                            getLogger(this).error(ex);
                            reportErrorWhenCrawlNews(sourceKey, ex);
                        } finally {
                            final int currentCompletedCount = completedCrawlNewsSourceCount.incrementAndGet();
                            getLogger(this).info("[complete craw news status: $current/$total]"
                                    .replace("$current", String.valueOf(currentCompletedCount))
                                    .replace("$total", String.valueOf(newsSourceSize))
                            );

                            if (currentCompletedCount == newsSourceSize) {
                                getLogger(this).info("<< Finish crawl news from web");
                                loadFromLocalAndPushToServer(newsSourceKeys, newsSourcesJsonConfig);
                            }
                        }
                    });
                } catch (Throwable ex) {
                    getLogger(this).error(ex);
                    reportErrorWhenCrawlNews(sourceKey, ex);
                } finally {
                    getLogger(this).info("complete execute for crawl source news of sourceKey: " + sourceKey + "; sourceKeyIdx: " + sourceKeyIdx + "; completedCrawlNewsSourceCount: " + completedCrawlNewsSourceCount.get() + "; total: " + newsSourceSize);
                }

//                break; //-- Mark: TESTING
            }
        }
    }

    private void reportErrorWhenCrawlNews(String sourceKey, Throwable ex) {
        try {
            String exStacktrace = "";
            try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ex.printStackTrace(
                        new PrintStream(baos)
                );
                baos.flush();

                exStacktrace = baos.toString(StandardCharsets.UTF_8);
            }
            final Map<String, Object> errorData = new LinkedHashMap<>();
            errorData.put("sourceKey", sourceKey);
            errorData.put("errorMessage", ex.getMessage());
            errorData.put("stackTrace", exStacktrace);
            errorData.put("timestamp", ZonedDateTime.now().format(dateTimeFormatter));
            crawlErrorCollector.add(errorData);

            gmailService.sendMail(
                    new MailEntity()
                            .setRecipientMails(
                                    List.of("nguyenminhhieu.geek@gmail.com")
                            )
                            .setSubject("Error when crawl news from %s".formatted(sourceKey))
                            .setHtmlBody(
                                    MessageFormat.format("""
                                                    <h2>Error when crawl news from {0}: {1} - {2}<h2>
                                                    <hr>
                                                    Details:
                                                    <pre>
                                                        {3}
                                                    </pre>
                                                    """
                                            , sourceKey
                                            , ex.getClass().getName()
                                            , ex.getMessage()
                                            , exStacktrace)
                            )

            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadFromLocalAndPushToServer(List<String> newsSourceKeys, JSONObject newsSourcesJsonConfig) {
        try {
            getLogger(this).info("Load from local and push to server >>>");
            Collections.shuffle(newsSourceKeys);
            for (final String sourceKey : newsSourceKeys) {
                getLogger(this).info("Load from local and push to server >>> sourceKey: %s ".formatted(sourceKey));
                try {
                    final List<NewsEntity> combinedNewsOfSourceKey = loadFromLocalFile(sourceKey);
                    getLogger(this).info("Load from local and push to server >>> sourceKey: %s -> loaded news size: %s".formatted(sourceKey, combinedNewsOfSourceKey.size()));

                    final List<NewsEntity> filteredNewsItems = new ArrayList<>(
                            combinedNewsOfSourceKey
                                    .stream()
                                    .filter(this::isValidFilteredNews)
                                    .map(this::censorFilteredWords)
                                    .distinct()
                                    .toList()
                    );
                    Collections.shuffle(filteredNewsItems);

                    publishNewsItemsToServer(sourceKey, filteredNewsItems);
                } catch (Throwable e) {
                    getLogger(this).error(e);
                }
            }

            cleanUpExpiredNewsItems();
            writeNewsSummaryDocument(newsSourceKeys);
        } catch (Throwable ex) {
            getLogger(this).error(ex);
        }
    }


    private List<NewsEntity> loadFromLocalFile(String sourceKey) throws IOException {
        try (final InputStream fis = Files.newInputStream(prepareLocalFileForNews(sourceKey));
             final BufferedInputStream bis = new BufferedInputStream(fis)) {
            final JSONArray jsonArray = new JSONArray(
                    IOStreamUtil.convertInputStreamToString(bis)
            );
            final int jsonObjLength = jsonArray.length();

            final List<NewsEntity> combinedNewsOfSourceKey = new ArrayList<>();
            for (int jsonIdx = 0; jsonIdx < jsonObjLength; ++jsonIdx) {
                final JSONObject jsonObject = jsonArray.getJSONObject(jsonIdx);
                combinedNewsOfSourceKey.add(NewsEntity.fromJson(jsonObject));
            }

            return combinedNewsOfSourceKey;
        }
    }

    private Map<String, Pair<List<NewsEntity>, List<Exception>>> crawlInSourceNews(JSONObject newsSources, String sourceKey, int sourceKeyIdx, int newsSourceKeysSize) throws Throwable {
        final Map<String, Pair<List<NewsEntity>, List<Exception>>> combinedNewsOfSourceKey = new HashMap<>();

        final JSONObject sourceInfo = newsSources.optJSONObject(sourceKey);

        //-- each link group carries its own language, derived from the group name
        crawlLinkGroup(combinedNewsOfSourceKey, sourceInfo.optJSONArray("vi-links"), "vi-VN", sourceKey, sourceInfo, sourceKeyIdx, newsSourceKeysSize);
        crawlLinkGroup(combinedNewsOfSourceKey, sourceInfo.optJSONArray("en-links"), "en-US", sourceKey, sourceInfo, sourceKeyIdx, newsSourceKeysSize);

        return combinedNewsOfSourceKey;
    }

    /**
     * Crawls every feed link in one language group and tags each article with the group's language.
     */
    private void crawlLinkGroup(Map<String, Pair<List<NewsEntity>, List<Exception>>> combinedNewsOfSourceKey,
                                JSONArray linkArray,
                                String language,
                                String sourceKey,
                                JSONObject sourceInfo,
                                int sourceKeyIdx,
                                int newsSourceKeysSize) throws Throwable {
        if (null == linkArray) {
            return;
        }

        final int linkArrayLength = linkArray.length();
        for (int sourceIndex = 0; sourceIndex < linkArrayLength; ++sourceIndex) {
            final long startTime = System.currentTimeMillis();

            final String newsLink = linkArray.getString(sourceIndex);

            try {
                final List<NewsEntity> newsEntities = crawlNewsFromSource(sourceKey, newsLink, sourceInfo, "sourceKey($sourceKeyStatus) - sourceArray($sourceArrayStatus)"
                        .replace("$sourceKeyStatus", (1 + sourceKeyIdx) + "/" + newsSourceKeysSize)
                        .replace("$sourceArrayStatus", (1 + sourceIndex) + "/" + linkArrayLength)
                );
                newsEntities.forEach(it -> it.setLanguage(language));
                combinedNewsOfSourceKey.put(newsLink, new Pair<>(newsEntities, Collections.emptyList()));
            } catch (Exception ex) {
                combinedNewsOfSourceKey.put(newsLink, new Pair<>(Collections.emptyList(), List.of(ex)));
            }

            final long waitingTime = MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS - (System.currentTimeMillis() - startTime);
            if (waitingTime > 0) {
                Thread.sleep(waitingTime);
            }
        }
    }

    private Path prepareLocalFileForNews(String... partialParts) throws IOException {
        final Path path_ = Paths.get(LOCAL_FOLDER_PATH
                , String.join("_", partialParts)
                        + ".json");

        if (Files.notExists(path_)) {
            if (Files.notExists(path_.getParent())) {
                Files.createDirectories(path_.getParent());
            }

            Files.createFile(path_);
        }

        return path_;
    }

    private void emptyLocalFolder() throws IOException {
        final Path path_ = Paths.get(LOCAL_FOLDER_PATH);
        if (Files.exists(path_)) {
            try (final Stream<Path> fileStream = Files.walk(path_)) {
                fileStream.map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private void saveToLocalFile(String sourceKey, List<NewsEntity> combinedNewsOfSourceKey) throws IOException {
        if (CollectionUtil.isNullOrEmpty(combinedNewsOfSourceKey)) {
            getLogger(this).info("ignore saveToLocalFile due to empty combinedNewsOfSourceKey");
            return;
        }

        try (final OutputStream fis = Files.newOutputStream(prepareLocalFileForNews(sourceKey));
             final BufferedOutputStream bos = new BufferedOutputStream(fis);
             final OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
            final Writer writer_ = new JSONArray(combinedNewsOfSourceKey).write(writer);
            writer_.flush();
            bos.flush();
        }
    }


    /**
     * Writes one document per news item so that items become queryable by source key
     * and orderable by published date. Runs are idempotent: the document id is derived
     * from the article link, so re-crawling the same article updates it in place.
     */
    private void publishNewsItemsToServer(String sourceKey, List<NewsEntity> newsItems) throws Throwable {
        if (newsItems.isEmpty()) {
            return;
        }

        final ZonedDateTime crawledAt = ZonedDateTime.now();

        firebaseWrapper
                .runWithWrapper(firebaseHelper -> {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    if (_firestoreOpt.isEmpty()) {
                        throw new IOException("Cannot obtain FirebaseHelper");
                    }

                    final Firestore firestore_ = _firestoreOpt.get();
                    final CollectionReference itemsCollection = firestore_.collection(FIRESTORE_ITEMS_COLLECTION_PATH);
                    final BulkWriter bulkWriter = firestore_.bulkWriter();

                    final List<DocumentReference> itemReferences = new ArrayList<>();
                    final Map<String, Map<String, Object>> freshDocuments = new LinkedHashMap<>();
                    for (final NewsEntity newsItem : newsItems) {
                        final String itemId = NewsItemDocumentMapper.itemIdOf(sourceKey, newsItem.getLink());
                        freshDocuments.put(itemId, NewsItemDocumentMapper.toDocument(
                                sourceKey,
                                newsItem,
                                crawledAt,
                                dateTimeFormatter
                        ));
                        itemReferences.add(itemsCollection.document(itemId));
                    }

                    final Map<String, Map<String, Object>> existingDocuments = new HashMap<>();
                    for (int fromIdx = 0; fromIdx < itemReferences.size(); fromIdx += EXISTING_ITEMS_READ_BATCH_SIZE) {
                        final DocumentReference[] batch = itemReferences
                                .subList(fromIdx, Math.min(fromIdx + EXISTING_ITEMS_READ_BATCH_SIZE, itemReferences.size()))
                                .toArray(DocumentReference[]::new);

                        for (final DocumentSnapshot existingDocument : firestore_.getAll(batch).get()) {
                            if (existingDocument.exists()) {
                                existingDocuments.put(existingDocument.getId(), existingDocument.getData());
                            }
                        }
                    }

                    int changedItemCount = 0;
                    for (final Map.Entry<String, Map<String, Object>> freshDocument : freshDocuments.entrySet()) {
                        if (NewsItemDocumentMapper.hasSameContent(
                                existingDocuments.get(freshDocument.getKey()),
                                freshDocument.getValue()
                        )) {
                            continue;
                        }

                        bulkWriter.set(itemsCollection.document(freshDocument.getKey()), freshDocument.getValue());
                        ++changedItemCount;
                    }

                    // flush every pending write before moving on to the next source
                    bulkWriter.close();

                    getLogger(this).infoFormat(
                            "published to item collection >>> sourceKey: %s ; items: %s ; written: %s ; skipped unchanged: %s",
                            sourceKey,
                            newsItems.size(),
                            changedItemCount,
                            newsItems.size() - changedItemCount
                    );
                });
    }

    /**
     * Deletes items that fell out of the retention window, replacing the previous
     * behaviour of wiping the whole collection before every run.
     */
    private void cleanUpExpiredNewsItems() throws Throwable {
        final Date expirationThreshold = Date.from(
                ZonedDateTime.now()
                        .minusHours(NEWS_RETENTION_HOURS)
                        .toInstant()
        );

        firebaseWrapper
                .runWithWrapper(firebaseHelper -> {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    if (_firestoreOpt.isEmpty()) {
                        throw new IOException("Cannot obtain FirebaseHelper");
                    }

                    final Firestore firestore_ = _firestoreOpt.get();
                    final CollectionReference itemsCollection = firestore_.collection(FIRESTORE_ITEMS_COLLECTION_PATH);
                    final BulkWriter bulkWriter = firestore_.bulkWriter();

                    long deletedCount = 0;
                    while (true) {
                        final List<QueryDocumentSnapshot> expiredDocuments = itemsCollection
                                .whereLessThan(NewsItemDocumentMapper.FIELD_PUB_DATE_TS, expirationThreshold)
                                .limit(CLEAN_UP_BATCH_SIZE)
                                .get()
                                .get()
                                .getDocuments();

                        if (expiredDocuments.isEmpty()) {
                            break;
                        }

                        for (final QueryDocumentSnapshot expiredDocument : expiredDocuments) {
                            bulkWriter.delete(expiredDocument.getReference());
                        }
                        deletedCount += expiredDocuments.size();

                        if (expiredDocuments.size() < CLEAN_UP_BATCH_SIZE) {
                            break;
                        }
                    }

                    bulkWriter.close();
                    getLogger(this).infoFormat("cleaned up expired news items: %s", deletedCount);
                });
    }

    /**
     * Publishes the total item count and the per-feed counts in a single document,
     * so the reader can render the filter chips without reading every item.
     */
    private void writeNewsSummaryDocument(List<String> newsSourceKeys) throws Throwable {
        firebaseWrapper
                .runWithWrapper(firebaseHelper -> {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    if (_firestoreOpt.isEmpty()) {
                        throw new IOException("Cannot obtain FirebaseHelper");
                    }

                    final Firestore firestore_ = _firestoreOpt.get();
                    final CollectionReference itemsCollection = firestore_.collection(FIRESTORE_ITEMS_COLLECTION_PATH);

                    final Map<String, Object> feedCounts = new LinkedHashMap<>();
                    long totalItems = 0;
                    for (final String sourceKey : newsSourceKeys) {
                        final long countOfSource = itemsCollection
                                .whereEqualTo(NewsItemDocumentMapper.FIELD_SOURCE_KEY, sourceKey)
                                .count()
                                .get()
                                .get()
                                .getCount();

                        if (countOfSource > 0) {
                            feedCounts.put(sourceKey, countOfSource);
                        }
                        totalItems += countOfSource;
                    }

                    final List<Map<String, Object>> errors = crawlErrorCollector.snapshot();
                    final Map<String, Object> summaryData = new LinkedHashMap<>();
                    summaryData.put("updatedTime", ZonedDateTime.now().format(dateTimeFormatter));
                    summaryData.put("totalItems", totalItems);
                    summaryData.put("feeds", feedCounts);
                    summaryData.put("errors", errors);
                    summaryData.put("lastError", FieldValue.delete());

                    firestore_.collection(FIRESTORE_NEWS_SUMMARY_PATH)
                            .document(FIRESTORE_NEWS_SUMMARY_DOC_ID)
                            .set(summaryData, SetOptions.merge())
                            .get();

                    getLogger(this).infoFormat("published news summary >>> totalItems: %s ; feeds: %s ; errors: %s", totalItems, feedCounts.keySet(), errors.size());
                });
    }


    private List<NewsEntity> crawlNewsFromSource(String sourceKey, String sourceUrl, JSONObject sourceInfo, String statusText) throws Exception {
        getLogger(this).infoFormat("source: %s ; data: %s ; status: %s ", sourceKey, sourceUrl, statusText);
        final byte[] respData = httpHelper.get(new RequestHttpBuilder().setUrl(sourceUrl));
        final Charset respCharset = detectResponseCharset(respData);
        final String respContent = new String(respData, respCharset);

        JSONObject prettyRespContent;
        try {
            prettyRespContent = XML.toJSONObject(respContent, false);
            return convertJsonToNewsEntity(prettyRespContent, sourceUrl, sourceInfo);
        } catch (JSONException exJ) {
            throw new JSONException("Response is not valid: " + StringUtil.truncate(respContent, 500), exJ);
        }
    }

    /**
     * Detects the character encoding of an HTTP response body so that multi-byte
     * encodings (e.g. UTF-16 served by some RSS feeds such as vietnamnews.vn) are
     * decoded correctly instead of being misread as the platform default (UTF-8).
     *
     * Detection order:
     *  1. UTF-16 BOM (FF FE = LE, FE FF = BE)
     *  2. UTF-16 LE pattern without BOM (alternating non-null byte + null byte)
     *  3. Fall back to UTF-8
     */
    private Charset detectResponseCharset(byte[] data) {
        if (data.length >= 2) {
            if (data[0] == (byte) 0xFF && data[1] == (byte) 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (data[0] == (byte) 0xFE && data[1] == (byte) 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
        }

        // UTF-16 LE without BOM: sample first bytes for non-null ASCII followed by 0x00
        int nullByteCount = 0;
        int sampleSize = Math.min(data.length, 100);
        for (int i = 1; i < sampleSize; i += 2) {
            if (data[i] == 0) {
                nullByteCount++;
            }
        }
        if (nullByteCount > sampleSize / 4) {
            return StandardCharsets.UTF_16LE;
        }

        return StandardCharsets.UTF_8;
    }

    @Nullable
    private List<NewsEntity> convertJsonToNewsEntity(JSONObject prettyRespContent, String sourceUrl, JSONObject sourceInfo) {
        if (null == prettyRespContent) {
            return null;
        }

        if (prettyRespContent.has("rss")) {
            return convertJsonToNewsEntityByStartKeyRss(prettyRespContent, sourceUrl, sourceInfo);
        } else if (prettyRespContent.has("feed")) {
            return convertJsonToNewsEntityByStartKeyFeed(prettyRespContent, sourceUrl, sourceInfo);
        } else {
            getLogger(this).error("ERR_NOT_SUPPORT_FEED! This feed data does not support: " + prettyRespContent);
            return null;
        }
    }

    private boolean isValidFilteredNews(NewsEntity newsEntity) {
        return FILTERED_WORD_PATTERNS.values().stream().noneMatch(wordPattern ->
                wordPattern
                        .matcher(StringUtil.trimWithNull(newsEntity.getTitle()))
                        .find()
        ) &&
                null != newsEntity.getPubDate() &&
                newsEntity.getPubDate().isAfter(
                        ZonedDateTime.now().minusDays(1)
                );
    }

    private NewsEntity censorFilteredWords(NewsEntity newsEntity) {
        FILTERED_WORD_PATTERNS.forEach((rawWord, realPattern) ->
                newsEntity.setDescription(newsEntity.getDescription().replace(realPattern.pattern(), "*" + rawWord + "*"))
        );
        return newsEntity;
    }

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyRss(JSONObject prettyRespContent, String sourceUrl, JSONObject sourceInfo) {
//        items = r.rss.channel[0].item.map((it) => ({
//            title: getItemAt0(it.title),
//            description: prettierDescription(getItemAt0(it.description)),
//            link: parseLinkFromFeed(getItemAt0(it.link)),
//            pubDate: getItemAt0(it.pubDate),
//            source: parseSourceFromLink(getItemAt0(it.link)),
//        }));

        final JSONArray itemJsonArray = prettyRespContent
                .getJSONObject("rss")
                .getJSONObject("channel")
                .getJSONArray("item");


        final String datePattern = sourceInfo.optString("datePattern");
        final ArrayList<NewsEntity> newsEntities = new ArrayList<>();
        if (null != itemJsonArray) {
            for (int itemIdx = 0; itemIdx < itemJsonArray.length(); ++itemIdx) {
                final JSONObject itemJson = itemJsonArray.getJSONObject(itemIdx);

                final NewsEntity newsEntity = new NewsEntity()
                        .setTitle(itemJson.optString("title"))
                        .setDescription(
                                itemJson.optString("content:encoded", itemJson.optString("description"))
                        )
                        .setLink(CrawlNewsHelper.parseLinkFromFeed(
                                itemJson.optString("link")
                        ))
                        .setPubDate(
                                DateUtil.convertToZonedDateTime(
                                        DateUtil.parse(itemJson.optString("pubDate"), datePattern)
                                )
                        )
                        .setSourceDomain(CrawlNewsHelper.parseSourceFromLink(
                                itemJson.optString("link")
                        ))
                        .setSourceUrl(sourceUrl);
                newsEntity.setCoverImageSrc(
                        CrawlNewsHelper.obtainCoverImageFromNews(newsEntity, itemJson)
                );

                newsEntities.add(newsEntity);
            }
        }

        return newsEntities;
    }

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyFeed(JSONObject prettyRespContent, String sourceUrl, JSONObject sourceInfo) {
//        items = r.feed.entry.map((it) => ({
//            title: getItemAt0(it.title),
//            description: prettierDescription(getItemAt0(it.description)),
//            link: parseLinkFromFeed(getItemAt0(it.link)),
//            pubDate: getItemAt0(it.pubDate),
//            source: parseSourceFromLink(
//                    parseLinkFromFeed(getItemAt0(it.link))
//            ),
//        }));
        final JSONArray itemJsonArray = prettyRespContent
                .getJSONObject("feed")
                .getJSONArray("entry");

        final String datePattern = sourceInfo.optString("datePattern");

        final ArrayList<NewsEntity> newsEntities = new ArrayList<>();
        if (null != itemJsonArray) {
            for (int itemIdx = 0; itemIdx < itemJsonArray.length(); ++itemIdx) {
                final JSONObject itemJson = itemJsonArray.getJSONObject(itemIdx);
                final NewsEntity newsEntity = new NewsEntity()
                        .setTitle(itemJson.getString("title"))
                        .setDescription(
                                itemJson.optJSONObject("content").optString("content")
                        )
                        .setLink(CrawlNewsHelper.parseLinkFromFeed(
                                itemJson.optJSONObject("link").optString("href")
                        ))
                        .setPubDate(
                                DateUtil.convertToZonedDateTime(
                                        DateUtil.parse(
                                                itemJson.optString("published"),
                                                datePattern
                                        )
                                )
                        )
                        .setSourceDomain(CrawlNewsHelper.parseSourceFromLink(
                                itemJson.optJSONObject("link").optString("href")
                        ))
                        .setSourceUrl(sourceUrl);

                newsEntity.setCoverImageSrc(
                        CrawlNewsHelper.obtainCoverImageFromNews(newsEntity, itemJson)
                );

                newsEntities.add(newsEntity);
            }
        }

        return newsEntities;
    }
}
