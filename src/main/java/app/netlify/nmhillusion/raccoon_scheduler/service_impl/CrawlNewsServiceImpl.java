package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.n2mix.exception.GeneralException;
import app.netlify.nmhillusion.n2mix.helper.YamlReader;
import app.netlify.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import app.netlify.nmhillusion.n2mix.helper.http.HttpHelper;
import app.netlify.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import app.netlify.nmhillusion.n2mix.type.ChainMap;
import app.netlify.nmhillusion.n2mix.util.StringUtil;
import app.netlify.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import app.netlify.nmhillusion.raccoon_scheduler.entity.NewsEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */
@Service
public class CrawlNewsServiceImpl extends BaseSchedulerServiceImpl implements CrawlNewsService {
    //    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private static final String FIRESTORE_COLLECTION_PATH = "raccoon-scheduler--news";
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private final List<String> DISABLED_SOURCES = new ArrayList<>();
    private final Map<String, Pattern> FILTERED_WORD_PATTERNS = new HashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final AtomicInteger completedCrawlNewsSourceCount = new AtomicInteger();
    private final HttpHelper httpHelper = new HttpHelper();

    @Autowired
    private FirebaseWrapper firebaseWrapper;

    private int BUNDLE_SIZE = 100;
    @Value("${format.date-time}")
    private String dateTimeFormat;

    @Value("${service.crawl-news.enable}")
    private boolean enableExecution;

    @PostConstruct
    private void init() {
        try (final InputStream crawlNewsStream = getClass().getClassLoader().getResourceAsStream("service-config/crawl-news.yml")) {
            final YamlReader yamlReader = new YamlReader(crawlNewsStream);

            BUNDLE_SIZE = yamlReader.getProperty("source-news.throttle.bundle.size", Integer.class);

            final String rawDisabledSources = yamlReader.getProperty("source-news.disabled-sources", String.class);
            DISABLED_SOURCES.addAll(Arrays.stream(rawDisabledSources.split(",")).map(String::trim).filter(it -> 0 < it.length()).toList());

            final String rawFilteredWords = yamlReader.getProperty("source-news.filter-words", String.class);
            final String[] filteredWordArray = rawFilteredWords.split(",");
            Arrays.stream(filteredWordArray)
                    .map(String::trim)
                    .filter(Predicate.not(String::isBlank))
                    .map(word -> new ChainMap<String, Pattern>().chainPut(word, Pattern.compile("(^|\\W)" + word + "(\\W|$)", Pattern.CASE_INSENSITIVE)))
                    .forEach(FILTERED_WORD_PATTERNS::putAll);


            getLog(this).info("BUNDLE_SIZE: " + BUNDLE_SIZE);
            getLog(this).info("DISABLED_SOURCES: " + DISABLED_SOURCES);
            getLog(this).info("rawFilteredWords: " + rawFilteredWords + "; FILTERED_WORDS: " + FILTERED_WORD_PATTERNS + "; size: " + FILTERED_WORD_PATTERNS.size());
        } catch (Exception ex) {
            getLog(this).error(ex);
        }
    }


    @Override
    public boolean isEnableExecution() {
        return enableExecution;
    }

    @Override
    public void doExecute() throws Throwable {
        try (final InputStream newsSourceStream = getClass().getClassLoader().getResourceAsStream("data/news-sources.json")) {
            final JSONObject newsSources = new JSONObject(StreamUtils.copyToString(newsSourceStream, StandardCharsets.UTF_8));
            final Map<String, List<NewsEntity>> combinedNewsData = new HashMap<>();
            getLog(this).info("Start crawl news from web >>");

            clearOldNewsData();
            completedCrawlNewsSourceCount.setOpaque(0);

            final List<String> newsSourceKeys = newsSources.keySet().stream().toList();
            for (int sourceKeyIdx = 0; sourceKeyIdx < newsSourceKeys.size(); ++sourceKeyIdx) {
                final String sourceKey = newsSourceKeys.get(sourceKeyIdx);

                if (DISABLED_SOURCES.contains(sourceKey)) {
                    getLog(this).warn("SKIP source key: " + sourceKey);
                    continue;
                }

                int finalSourceKeyIdx = sourceKeyIdx;
                executorService.execute(() -> {
                    try {
                        crawlInSourceNews(newsSources, sourceKey, finalSourceKeyIdx,
                                newsSourceKeys.size());
                    } catch (ExecutionException | InterruptedException | IOException | GeneralException e) {
                        getLog(this).error(e);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });

            }
            getLog(this).info("<< Finish crawl news from web");
        }
    }

    private void crawlInSourceNews(JSONObject newsSources, String sourceKey, int sourceKeyIdx, int newsSourceKeysSize) throws Throwable {
        final List<NewsEntity> combinedNewsOfSourceKey = new ArrayList<>();

        final JSONArray sourceArray = newsSources.optJSONArray(sourceKey);
        final int sourceArrayLength = sourceArray.length();
        for (int sourceIndex = 0; sourceIndex < sourceArrayLength; ++sourceIndex) {
            final long startTime = System.currentTimeMillis();

            combinedNewsOfSourceKey.addAll(
                    crawlNewsFromSource(sourceKey, sourceArray.getString(sourceIndex), "sourceKey($sourceKeyStatus) - sourceArray($sourceArrayStatus)"
                            .replace("$sourceKeyStatus", (1 + sourceKeyIdx) + "/" + newsSourceKeysSize)
                            .replace("$sourceArrayStatus", (1 + sourceIndex) + "/" + sourceArrayLength)
                    )
            );

            while (MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS > System.currentTimeMillis() - startTime)
                ;
        }

        final List<Map.Entry<String, List<NewsEntity>>> newsItemBundles = splitItemsToBundle(sourceKey, combinedNewsOfSourceKey
                .stream()
                .filter(this::isValidFilteredNews)
                .map(this::censorFilteredWords)
                .toList()
        );
        for (Map.Entry<String, List<NewsEntity>> _bundle : newsItemBundles) {
            pushSourceNewsToServer(_bundle);
        }

        getLog(this).info("[complete status: $current/$total]"
                .replace("$current", String.valueOf(completedCrawlNewsSourceCount.incrementAndGet()))
                .replace("$total", String.valueOf(newsSourceKeysSize))
        );
    }

    private synchronized void clearOldNewsData() throws Throwable {
        getLog(this).info("Do clear old news data");

        firebaseWrapper.setFirebaseConfig(FirebaseConfigConstant.getInstance().getFirebaseConfig())
                .runWithWrapper(firebaseHelper ->
                {
                    final Optional<Firestore> _firestoreOpt = firebaseHelper.getFirestore();
                    Optional<CollectionReference> rsNewsColtOpt = Optional.empty();
                    if (_firestoreOpt.isPresent()) {
                        rsNewsColtOpt = Optional.of(
                                _firestoreOpt.get().collection(FIRESTORE_COLLECTION_PATH)
                        );

                        rsNewsColtOpt.ifPresent(coltRef -> coltRef.listDocuments().forEach(DocumentReference::delete));
                    }

                });
    }

    private synchronized void pushSourceNewsToServer(Map.Entry<String, List<NewsEntity>> _bundle) throws Throwable {
        firebaseWrapper.setFirebaseConfig(FirebaseConfigConstant.getInstance().getFirebaseConfig())
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
                    docsData.put("updatedTime", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateTimeFormat)));
                    docsData.put(_bundle.getKey(), _bundle.getValue());
                    final ApiFuture<DocumentReference> resultApiFuture = rsNewsColtOpt.get().add(docsData);

                    final DocumentReference writeResult = resultApiFuture.get();
                    getLog(this).infoFormat("result update news [%s -> size: %s]: %s", "data." + _bundle.getKey(), _bundle.getValue().size(), writeResult);
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

    private List<NewsEntity> crawlNewsFromSource(String sourceKey, String sourceUrl, String statusText) {
        getLog(this).infoFormat("source: %s ; data: %s ; status: %s ", sourceKey, sourceUrl, statusText);
        try {
            final byte[] respData = httpHelper.get(new RequestHttpBuilder().setUrl(sourceUrl));
            final String respContent = new String(respData);
            final JSONObject prettyRespContent = XML.toJSONObject(respContent, false);

            return convertJsonToNewsEntity(prettyRespContent, sourceUrl);
        } catch (Exception ex) {
            getLog(this).error(ex);
            return new ArrayList<>();
        }
    }

    @Nullable
    private List<NewsEntity> convertJsonToNewsEntity(JSONObject prettyRespContent, String sourceUrl) {
        if (null == prettyRespContent) {
            return null;
        }

        if (prettyRespContent.has("rss")) {
            return convertJsonToNewsEntityByStartKeyRss(prettyRespContent, sourceUrl);
        } else if (prettyRespContent.has("feed")) {
            return convertJsonToNewsEntityByStartKeyFeed(prettyRespContent, sourceUrl);
        } else {
            getLog(this).error("ERR_NOT_SUPPORT_FEED! This feed data does not support: " + prettyRespContent);
            return null;
        }
    }

    private boolean isValidFilteredNews(NewsEntity newsEntity) {
        return FILTERED_WORD_PATTERNS.values().stream().noneMatch(wordPattern ->
                wordPattern
                        .matcher(StringUtil.trimWithNull(newsEntity.getTitle()))
                        .find()
        );
    }

    private NewsEntity censorFilteredWords(NewsEntity newsEntity) {
        FILTERED_WORD_PATTERNS.forEach((rawWord, realPattern) ->
                newsEntity.setDescription(newsEntity.getDescription().replace(realPattern.pattern(), "*" + rawWord + "*"))
        );
        return newsEntity;
    }

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyRss(JSONObject prettyRespContent, String sourceUrl) {
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
                                itemJson.optString("pubDate")
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

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyFeed(JSONObject prettyRespContent, String sourceUrl) {
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
                                itemJson.optString("published")
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
