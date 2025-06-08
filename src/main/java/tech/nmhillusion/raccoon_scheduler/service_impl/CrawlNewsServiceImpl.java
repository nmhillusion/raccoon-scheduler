package tech.nmhillusion.raccoon_scheduler.service_impl;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.json.JSONArray;
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
import tech.nmhillusion.raccoon_scheduler.entity.news.FirebaseNewsEntity;
import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;
import tech.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import tech.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import tech.nmhillusion.raccoon_scheduler.service.GmailService;

import javax.annotation.PostConstruct;
import java.io.*;
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
    private static final String FIRESTORE_COLLECTION_PATH = "raccoon-scheduler--news";
    private static final String FIRESTORE_COLLECTION_NEWS_STATE_PATH = "raccoon-scheduler--news--state";
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private static final String LOCAL_FOLDER_PATH = "temp-data";
    private final List<String> DISABLED_SOURCES = new ArrayList<>();
    private final Map<String, Pattern> FILTERED_WORD_PATTERNS = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicInteger completedCrawlNewsSourceCount = new AtomicInteger();
    private final AtomicInteger completedPushedNewsToServerCount = new AtomicInteger();
    private final HttpHelper httpHelper = new HttpHelper();
    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();
    private final GmailService gmailService;
    private int BUNDLE_SIZE = 100;
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

            BUNDLE_SIZE = yamlReader.getProperty("source-news.throttle.bundle.size", Integer.class);

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

            getLogger(this).info("BUNDLE_SIZE: " + BUNDLE_SIZE);
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
                    Optional<CollectionReference> rsNewsColtOpt = Optional.empty();
                    if (_firestoreOpt.isPresent()) {
                        final Firestore firestore_ = _firestoreOpt.get();

                        final CollectionReference stateCollection = firestore_.collection(FIRESTORE_COLLECTION_NEWS_STATE_PATH);

                        final DocumentReference sourcesDocRef = stateCollection.document("sources");
                        final ApiFuture<DocumentSnapshot> documentSnapshotApiFuture = sourcesDocRef.get();
                        final DocumentSnapshot currentSourcesData = documentSnapshotApiFuture.get();
                        final String fbLastModifiedTime = currentSourcesData.get("lastModifiedTime", String.class);

                        if (!String.valueOf(updatedDateOfNewsSource).equals(fbLastModifiedTime)) {
                            final ApiFuture<WriteResult> pushStateFuture = sourcesDocRef.update(
                                    new ChainMap<String, Object>()
                                            .chainPut("lastModifiedTime", updatedDateOfNewsSource)
                                            .chainPut("data", newsSourceList)
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

            clearOldNewsData();
            emptyLocalFolder();
            completedCrawlNewsSourceCount.setOpaque(0);
            completedPushedNewsToServerCount.setOpaque(0);

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

                    final List<Map.Entry<String, List<NewsEntity>>> newsItemBundles = splitItemsToBundle(sourceKey, filteredNewsItems);

                    newsItemBundles.parallelStream()
                            .forEach(bundle_ -> {
                                try {
                                    final int dataIndexForServer = completedPushedNewsToServerCount.incrementAndGet();

                                    getLogger(this).info(
                                            ("Pushing news to server >>> sourceKey: %s ; " +
                                                    "dataIndexForServer: %s ; " +
                                                    "bundle key: %s; bundle size: %s").formatted(
                                                    sourceKey,
                                                    dataIndexForServer,
                                                    bundle_.getKey(),
                                                    bundle_.getValue().size())
                                    );

                                    pushSourceNewsToServer(
                                            dataIndexForServer
                                            , sourceKey
                                            , bundle_
                                            , newsSourcesJsonConfig.optJSONObject(sourceKey)
                                    );
                                } catch (Throwable e_) {
                                    getLogger(this).error(e_);
                                }
                            });
                } catch (Throwable e) {
                    getLogger(this).error(e);
                }
            }
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
        final JSONArray linkArray = sourceInfo.optJSONArray("links");
        final int linkArrayLength = linkArray.length();

        for (int sourceIndex = 0; sourceIndex < linkArrayLength; ++sourceIndex) {
            final long startTime = System.currentTimeMillis();


            final String newsLink = linkArray.getString(sourceIndex);

            try {
                final List<NewsEntity> newsEntities = crawlNewsFromSource(sourceKey, newsLink, sourceInfo, "sourceKey($sourceKeyStatus) - sourceArray($sourceArrayStatus)"
                        .replace("$sourceKeyStatus", (1 + sourceKeyIdx) + "/" + newsSourceKeysSize)
                        .replace("$sourceArrayStatus", (1 + sourceIndex) + "/" + linkArrayLength)
                );

                combinedNewsOfSourceKey.put(newsLink, new Pair<>(newsEntities, Collections.emptyList()));
            } catch (Exception ex) {
                combinedNewsOfSourceKey.put(newsLink, new Pair<>(Collections.emptyList(), List.of(ex)));
            }

            final long waitingTime = MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS - (System.currentTimeMillis() - startTime);
            if (waitingTime > 0) {
                Thread.sleep(waitingTime);
            }
        }
        return combinedNewsOfSourceKey;
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

    private synchronized void clearOldNewsData() throws Throwable {
        getLogger(this).info("Do clear old news data");

        firebaseWrapper
                .runWithWrapper(firebaseHelper ->
                {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    Optional<CollectionReference> rsNewsColtOpt = Optional.empty();
                    if (_firestoreOpt.isPresent()) {
                        final Firestore firestore_ = _firestoreOpt.get();

                        firestore_.collection(FIRESTORE_COLLECTION_PATH).add(
                                new ChainMap<String, String>()
                                        .chainPut("sampleData", "this content is a sample")
                        );

                        rsNewsColtOpt = Optional.of(
                                firestore_.collection(FIRESTORE_COLLECTION_PATH)
                        );

                        rsNewsColtOpt.ifPresent(coltRef -> coltRef.listDocuments().forEach(DocumentReference::delete));
                    }

                });
    }

    private void pushSourceNewsToServer(int dataIndexForServer, String sourceName, Map.Entry<String, List<NewsEntity>> _newsBundle, JSONObject sourceInfo) throws Throwable {
        firebaseWrapper
                .runWithWrapper(firebaseHelper -> {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    Optional<CollectionReference> rsNewsColtOpt = Optional.empty();
                    if (_firestoreOpt.isPresent()) {
                        rsNewsColtOpt = Optional.of(
                                _firestoreOpt.get().collection(FIRESTORE_COLLECTION_PATH)
                        );
                    }

                    if (rsNewsColtOpt.isEmpty()) {
                        throw new IOException("Cannot obtain FirebaseHelper");
                    }

                    final Map<String, Object> docsData = new HashMap<>();
                    docsData.put("dataIndex", dataIndexForServer);
                    docsData.put("source", sourceName);
                    docsData.put("key", _newsBundle.getKey());
                    docsData.put("updatedTime", ZonedDateTime.now().format(dateTimeFormatter));
                    docsData.put("data", _newsBundle.getValue()
                            .stream()
                            .map(it -> FirebaseNewsEntity.fromNewsEntity(it, dateTimeFormatter))
                            .toList()
                    );
                    docsData.put("language", sourceInfo.optString("language"));
                    final ApiFuture<DocumentReference> resultApiFuture = rsNewsColtOpt.get().add(docsData);

                    final DocumentReference writeResult = resultApiFuture.get();
                    getLogger(this).infoFormat("result update news [%s -> size: %s]: %s", "data." + _newsBundle.getKey(), _newsBundle.getValue().size(), writeResult);
                });
    }

    private List<Map.Entry<String, List<NewsEntity>>> splitItemsToBundle(String newsSourceKey, List<NewsEntity> newsEntities) {
        final List<Map.Entry<String, List<NewsEntity>>> splitBundles = new ArrayList<>();
        final int bundleLength = (int) Math.ceil((float) newsEntities.size() / BUNDLE_SIZE);
        for (int bundleIdx = 0; bundleIdx < bundleLength; ++bundleIdx) {
            final int fromIndex = bundleIdx * BUNDLE_SIZE;
            final int endIndex = Math.min(fromIndex + BUNDLE_SIZE, newsEntities.size());
            splitBundles.add(
                    new AbstractMap.SimpleEntry<>(
                            newsSourceKey + "__" + bundleIdx,
                            newsEntities.subList(fromIndex, endIndex)
                    )
            );
        }
        return splitBundles;
    }

    private List<NewsEntity> crawlNewsFromSource(String sourceKey, String sourceUrl, JSONObject sourceInfo, String statusText) {
        getLogger(this).infoFormat("source: %s ; data: %s ; status: %s ", sourceKey, sourceUrl, statusText);
        try {
            final byte[] respData = httpHelper.get(new RequestHttpBuilder().setUrl(sourceUrl));
            final String respContent = new String(respData);
            final JSONObject prettyRespContent = XML.toJSONObject(respContent, false);

            return convertJsonToNewsEntity(prettyRespContent, sourceUrl, sourceInfo);
        } catch (Exception ex) {
            getLogger(this).error(
                    "Error for crawl news from [%s]: %s".formatted(sourceUrl, ex.getMessage())
            );
            getLogger(this).error(ex);
            return new ArrayList<>();
        }
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
        )
                &&
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
