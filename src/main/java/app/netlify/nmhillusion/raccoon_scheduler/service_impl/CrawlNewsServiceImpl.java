package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.NewsEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.HttpHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.firebase.FirebaseHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.util.YamlReader;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */
@Service
public class CrawlNewsServiceImpl implements CrawlNewsService {
    private static final int MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS = 5_000;
    private final List<String> DISABLED_SOURCES = new ArrayList<>();
    private final List<String> FILTERED_WORDS = new ArrayList<>();
    private int BUNDLE_SIZE = 100;
    @Autowired
    private HttpHelper httpHelper;
    @Value("${format.date-time}")
    private String dateTimeFormat;

    @PostConstruct
    private void init() {
        try (final InputStream crawlNewsStream = getClass().getClassLoader().getResourceAsStream("service-config/crawl-news.yml")) {
            final YamlReader yamlReader = new YamlReader(crawlNewsStream);

            BUNDLE_SIZE = yamlReader.getProperty("source-news.throttle.bundle.size", Integer.class);

            final String rawDisabledSources = yamlReader.getProperty("source-news.disabled-sources", String.class);
            DISABLED_SOURCES.addAll(Arrays.stream(rawDisabledSources.split(",")).map(String::trim).filter(it -> 0 < it.length()).toList());

            final String rawFilteredWords = yamlReader.getProperty("source-news.filter-words", String.class);
            FILTERED_WORDS.addAll(Arrays.stream(rawFilteredWords.split(",")).map(String::trim).filter(it -> 0 < it.length()).toList());

            getLog(this).info("BUNDLE_SIZE: " + BUNDLE_SIZE);
            getLog(this).info("DISABLED_SOURCES: " + DISABLED_SOURCES);
            getLog(this).info("FILTERED_WORDS: " + FILTERED_WORDS);
        } catch (Exception ex) {
            getLog(this).error(ex.getMessage(), ex);
        }
    }


    @Override
    public void execute() throws Exception {
        try (final InputStream newsSourceStream = getClass().getClassLoader().getResourceAsStream("data/news-sources.json");
             final FirebaseHelper firebaseHelper = new FirebaseHelper()
        ) {
            final Optional<FirebaseHelper> firebaseHelperOpt = firebaseHelper.newsInstance();

            if (firebaseHelperOpt.isPresent()) {
                final Optional<Firestore> _firestoreOpt = firebaseHelperOpt.get().getFirestore();
                Optional<CollectionReference> rsNewsColtOpt = Optional.empty();
                if (_firestoreOpt.isPresent()) {
                    rsNewsColtOpt = Optional.of(
                            _firestoreOpt.get().collection("raccoon-scheduler--news")
                    );

                    rsNewsColtOpt.ifPresent(coltRef -> coltRef.listDocuments().forEach(DocumentReference::delete));
                }

                final JSONObject newsSources = new JSONObject(StreamUtils.copyToString(newsSourceStream, StandardCharsets.UTF_8));
                final Map<String, List<NewsEntity>> combinedNewsData = new HashMap<>();
                getLog(this).info("Start crawl news from web >>");
                final List<String> newsSourceKeys = newsSources.keySet().stream().toList();
                for (int sourceKeyIdx = 0; sourceKeyIdx < newsSourceKeys.size(); ++sourceKeyIdx) {
                    final String sourceKey = newsSourceKeys.get(sourceKeyIdx);

                    if (DISABLED_SOURCES.contains(sourceKey)) {
                        LogHelper.getLog(this).warn("SKIP source key: " + sourceKey);
                        continue;
                    }

                    final List<NewsEntity> combinedNewsOfSourceKey = new ArrayList<>();

                    final JSONArray sourceArray = newsSources.optJSONArray(sourceKey);
                    final int sourceArrayLength = sourceArray.length();
                    for (int sourceIndex = 0; sourceIndex < sourceArrayLength; ++sourceIndex) {
                        final long startTime = System.currentTimeMillis();
                        combinedNewsOfSourceKey.addAll(
                                crawlNewsFromSource(sourceKey, sourceArray.getString(sourceIndex), "sourceKey($sourceKeyStatus) - sourceArray($sourceArrayStatus)"
                                        .replace("$sourceKeyStatus", (1 + sourceKeyIdx) + "/" + newsSourceKeys.size())
                                        .replace("$sourceArrayStatus", (1 + sourceIndex) + "/" + sourceArrayLength)
                                )
                        );

                        while (MIN_INTERVAL_CRAWL_NEWS_TIME_IN_MILLIS > System.currentTimeMillis() - startTime)
                            ;
                    }

                    final List<Map.Entry<String, List<NewsEntity>>> newsItemBundles = splitItemsToBundle(sourceKey, combinedNewsOfSourceKey);
                    for (Map.Entry<String, List<NewsEntity>> _bundle : newsItemBundles) {
                        if (rsNewsColtOpt.isPresent()) {
                            final Map<String, Object> docsData = new HashMap<>();
                            docsData.put("updatedTime", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateTimeFormat)));
                            docsData.put(_bundle.getKey(), _bundle.getValue());
                            final ApiFuture<DocumentReference> resultApiFuture = rsNewsColtOpt.get().add(docsData);

                            final DocumentReference writeResult = resultApiFuture.get();
                            getLog(this).info("result update news [{} -> size: {}]: {}", "data." + _bundle.getKey(), _bundle.getValue().size(), writeResult);
                        }
                    }
                }
                getLog(this).info("<< Finish crawl news from web");
            }
        }
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
        getLog(this).info("source: {} ; data: {} ; status: {} ", sourceKey, sourceUrl, statusText);
        try {
//            if (sourceKey.startsWith("medium")) { /// Mark: TESTING
//                return new ArrayList<>();
//            }

            final byte[] respData = httpHelper.get(sourceUrl);
            final String respContent = new String(respData);
            final JSONObject prettyRespContent = XML.toJSONObject(respContent, false);

            return convertJsonToNewsEntity(prettyRespContent, sourceUrl);
        } catch (Exception ex) {
            getLog(this).error(ex.getMessage(), ex);
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
        return FILTERED_WORDS.stream().anyMatch(word ->
                !String.valueOf(newsEntity.getTitle()).toLowerCase().contains(String.valueOf(word).toLowerCase())
                        && !String.valueOf(newsEntity.getDescription()).toLowerCase().contains(String.valueOf(word).toLowerCase())
        );
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

                if (isValidFilteredNews(newsEntity)) {
                    newsEntities.add(newsEntity);
                }
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
                
                if (isValidFilteredNews(newsEntity)) {
                    newsEntities.add(newsEntity);
                }
            }
        }

        return newsEntities;
    }
}
