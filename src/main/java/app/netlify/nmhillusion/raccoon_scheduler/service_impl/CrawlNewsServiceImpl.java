package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.news.FirebaseNewsEntity;
import app.netlify.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import tech.nmhillusion.n2mix.exception.GeneralException;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.type.ChainMap;
import tech.nmhillusion.n2mix.util.DateUtil;
import tech.nmhillusion.n2mix.util.StringUtil;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
    private final List<String> DISABLED_SOURCES = new ArrayList<>();
    private final Map<String, Pattern> FILTERED_WORD_PATTERNS = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicInteger completedCrawlNewsSourceCount = new AtomicInteger();
    private final AtomicInteger completedPushedNewsToServerCount = new AtomicInteger();
    private final HttpHelper httpHelper = new HttpHelper();
    private final FirebaseWrapper firebaseWrapper = FirebaseWrapper.getInstance();
    private int BUNDLE_SIZE = 100;
    @Value("${format.date-time}")
    private String dateTimeFormat;
    private DateTimeFormatter dateTimeFormatter;
    @Value("${service.crawl-news.enable}")
    private boolean enableExecution;

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

    private void updateForNewsSourceState(String newsSourcesFilename, List<String> newsSourceList) throws Throwable {
        final BasicFileAttributes basicFileAttributes = Files.readAttributes(Path.of(newsSourcesFilename), BasicFileAttributes.class);
        final FileTime lastModifiedTime = basicFileAttributes.lastModifiedTime();
        final long lastModifiedTimeMillis = lastModifiedTime.toMillis();

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
                        final Long fbLastModifiedTime = currentSourcesData.get("lastModifiedTime", Long.class);

                        if (null != fbLastModifiedTime) {
                            if (fbLastModifiedTime != lastModifiedTimeMillis) {

                                sourcesDocRef.update(
                                        new ChainMap<String, Object>()
                                                .chainPut("lastModifiedTime", lastModifiedTimeMillis)
                                                .chainPut("data", newsSourceList)
                                );

                            }
                        }
                    }
                });
    }

    @Override
    public void doExecute() throws Throwable {
        final String newsSourcesFilename = "data/news-sources.json";

        try (final InputStream newsSourceStream = getClass().getClassLoader().getResourceAsStream(newsSourcesFilename)) {
            final JSONObject newsSources = new JSONObject(StreamUtils.copyToString(newsSourceStream, StandardCharsets.UTF_8));
            getLogger(this).info("Start crawl news from web >>");

            clearOldNewsData();
            completedCrawlNewsSourceCount.setOpaque(0);
            completedPushedNewsToServerCount.setOpaque(0);

            final List<String> newsSourceKeys = new ArrayList<>(
                    newsSources.keySet().stream().toList()
            );
            Collections.shuffle(newsSourceKeys);

            getLogger(this).info("==> newsSourceKeys: %s".formatted(newsSourceKeys));
//            final List<String> newsSourceKeys = List.of("voa-tieng-viet"); /// Mark: TESTING
            for (int sourceKeyIdx = 0; sourceKeyIdx < newsSourceKeys.size(); ++sourceKeyIdx) {
                final String sourceKey = newsSourceKeys.get(sourceKeyIdx);

                if (DISABLED_SOURCES.contains(sourceKey)) {
                    getLogger(this).warn("SKIP source key: " + sourceKey);
                    continue;
                }

                try {
                    crawlInSourceNews(newsSources, sourceKey, sourceKeyIdx,
                            newsSourceKeys.size());
                } catch (ExecutionException | InterruptedException | IOException | GeneralException e) {
                    getLogger(this).error(e);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                } finally {
                    getLogger(this).info("complete execute for crawl source news of sourceKey: " + sourceKey + "; sourceKeyIdx: " + sourceKeyIdx + "; completedCrawlNewsSourceCount: " + completedCrawlNewsSourceCount.get() + "; total: " + newsSourceKeys.size());
                }

//                break; /// Mark: TESTING
            }
            getLogger(this).info("<< Finish crawl news from web");

            updateForNewsSourceState(newsSourcesFilename, newsSourceKeys);
        }
    }

    private void crawlInSourceNews(JSONObject newsSources, String sourceKey, int sourceKeyIdx, int newsSourceKeysSize) throws Throwable {
        final List<NewsEntity> combinedNewsOfSourceKey = new ArrayList<>();

        final JSONObject sourceInfo = newsSources.optJSONObject(sourceKey);
        final JSONArray linkArray = sourceInfo.optJSONArray("links");
        final int linkArrayLength = linkArray.length();
        for (int sourceIndex = 0; sourceIndex < linkArrayLength; ++sourceIndex) {
            final long startTime = System.currentTimeMillis();

            combinedNewsOfSourceKey.addAll(
                    crawlNewsFromSource(sourceKey, linkArray.getString(sourceIndex), sourceInfo, "sourceKey($sourceKeyStatus) - sourceArray($sourceArrayStatus)"
                            .replace("$sourceKeyStatus", (1 + sourceKeyIdx) + "/" + newsSourceKeysSize)
                            .replace("$sourceArrayStatus", (1 + sourceIndex) + "/" + linkArrayLength)
                    )
            );

            while (MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS > System.currentTimeMillis() - startTime)
                ;
        }

        executorService.submit(() -> {
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

            try {
                pushSourceNewsToServer(sourceKey
                        , newsItemBundles
                        , sourceInfo
                );
            } catch (Throwable ex) {
                getLogger(this).error("Fail to push news to server");
                getLogger(this).error(ex);
            }

//        final Optional<Map.Entry<String, List<NewsEntity>>> firstSourceOpt = newsItemBundles.stream().findFirst();
//        firstSourceOpt.ifPresent(it_ -> {
//            final Optional<NewsEntity> firstNews = it_.getValue().stream().findFirst();
//            getLogger(this).info("test data: " + firstNews);
//        });

            getLogger(this).info("[complete status: $current/$total]"
                    .replace("$current", String.valueOf(completedCrawlNewsSourceCount.incrementAndGet()))
                    .replace("$total", String.valueOf(newsSourceKeysSize))
            );
        });
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

    private void pushSourceNewsToServer(String sourceName, List<Map.Entry<String, List<NewsEntity>>> _bundleList, JSONObject sourceInfo) throws Throwable {
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

                    for (final Map.Entry<String, List<NewsEntity>> _bundle : _bundleList) {
                        final Map<String, Object> docsData = new HashMap<>();
                        docsData.put("dataIndex", completedPushedNewsToServerCount.incrementAndGet());
                        docsData.put("source", sourceName);
                        docsData.put("key", _bundle.getKey());
                        docsData.put("updatedTime", ZonedDateTime.now().format(dateTimeFormatter));
                        docsData.put("data", _bundle.getValue()
                                .stream()
                                .map(it -> FirebaseNewsEntity.fromNewsEntity(it, dateTimeFormatter))
                                .toList()
                        );
                        docsData.put("language", sourceInfo.optString("language"));
                        final ApiFuture<DocumentReference> resultApiFuture = rsNewsColtOpt.get().add(docsData);

                        final DocumentReference writeResult = resultApiFuture.get();
                        getLogger(this).infoFormat("result update news [%s -> size: %s]: %s", "data." + _bundle.getKey(), _bundle.getValue().size(), writeResult);
                    }
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
                        ZonedDateTime.now().minusDays(2)
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
