package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.raccoon_scheduler.entity.NewsEntity;
import app.netlify.nmhillusion.raccoon_scheduler.helper.CrawlNewsHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.HttpHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.firebase.FirebaseHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.util.YamlReader;
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
    private int bundleSize = 100;
    @Autowired
    private HttpHelper httpHelper;
    @Value("${format.date-time}")
    private String dateTimeFormat;

    @PostConstruct
    private void init() {
        try (final InputStream crawlNewsStream = getClass().getClassLoader().getResourceAsStream("service-config/crawl-news.yml")) {
            final YamlReader yamlReader = new YamlReader(crawlNewsStream);

            bundleSize = yamlReader.getProperty("throttle.source-news.bundle.size", Integer.class);
        } catch (Exception ex) {
            getLog(this).error(ex.getMessage(), ex);
        }
    }



    @Override
    public void execute() throws Exception {
        try (final InputStream newsSourceStream = getClass().getClassLoader().getResourceAsStream("data/news-sources.json")
        ) {
            final JSONObject newsSources = new JSONObject(StreamUtils.copyToString(newsSourceStream, StandardCharsets.UTF_8));
            final Map<String, List<NewsEntity>> combinedNewsData = new HashMap<>();
            getLog(this).info("Start crawl news from web >>");
            final List<String> newsSourceKeys = newsSources.keySet().stream().toList();
            for (int sourceKeyIdx = 0; sourceKeyIdx < newsSourceKeys.size(); ++sourceKeyIdx) {
                final String sourceKey = newsSourceKeys.get(sourceKeyIdx);
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

                combinedNewsData.put(sourceKey, combinedNewsOfSourceKey);
            }
            getLog(this).info("<< Finish crawl news from web");
            updateToFirestore(combinedNewsData);
        }
    }

    private void updateToFirestore(Map<String, List<NewsEntity>> combinedNewsData) throws Exception {
        if (!FirebaseHelper.isEnable()) {
            getLog(this).warn("Firebase is not enable to update news to Firestore");
            return;
        }

        getLog(this).info("Start push news to Firestore >>");
        try (final FirebaseHelper firebaseHelper = new FirebaseHelper()) {
            final Firestore _firestore = firebaseHelper.getFirestore();
            final DocumentReference newsDocRef = _firestore.collection("raccoon-scheduler").document("news");
            combinedNewsData.forEach((newsSourceKey, newsEntities) -> {
                final List<Map.Entry<String, List<NewsEntity>>> newsItemBundles = splitItemsToBundle(newsSourceKey, newsEntities);

                for (Map.Entry<String, List<NewsEntity>> _bundle : newsItemBundles) {
                    getLog(this).info("do update for source: " + "data." + _bundle.getKey());
                    newsDocRef.update("data." + _bundle.getKey(), _bundle.getValue());
                }
            });
            newsDocRef.update("updatedTime", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateTimeFormat)));

            getLog(this).info("<< Finish push news to Firestore");
        }
    }

    private List<Map.Entry<String, List<NewsEntity>>> splitItemsToBundle(String newsSourceKey, List<NewsEntity> newsEntities) {
        final List<Map.Entry<String, List<NewsEntity>>> splitBundles = new ArrayList<>();
        final int bundleLength = (int) Math.ceil((float) newsEntities.size() / bundleSize);
        for (int bundleIdx = 0; bundleIdx < bundleLength; ++bundleIdx) {
            final int fromIndex = bundleIdx * bundleSize;
            final int endIndex = Math.min(fromIndex + bundleSize, newsEntities.size());
            splitBundles.add(
                    new AbstractMap.SimpleEntry<>(
                            newsSourceKey + "[" + bundleIdx + "]",
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

            return convertJsonToNewsEntity(prettyRespContent);
        } catch (Exception ex) {
            getLog(this).error(ex.getMessage(), ex);
            return new ArrayList<>();
        }
    }

    @Nullable
    private List<NewsEntity> convertJsonToNewsEntity(JSONObject prettyRespContent) {
        if (null == prettyRespContent) {
            return null;
        }

        if (prettyRespContent.has("rss")) {
            return convertJsonToNewsEntityByStartKeyRss(prettyRespContent);
        } else if (prettyRespContent.has("feed")) {
            return convertJsonToNewsEntityByStartKeyFeed(prettyRespContent);
        } else {
            getLog(this).error("ERR_NOT_SUPPORT_FEED! This feed data does not support: " + prettyRespContent);
            return null;
        }
    }

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyRss(JSONObject prettyRespContent) {
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
                        .setSource(CrawlNewsHelper.parseSourceFromLink(
                                itemJson.optString("link")
                        ));
                newsEntity.setCoverImageSrc(
                        CrawlNewsHelper.obtainCoverImageFromNews(newsEntity, itemJson)
                );
                newsEntities.add(newsEntity);
            }
        }

        return newsEntities;
    }

    private List<NewsEntity> convertJsonToNewsEntityByStartKeyFeed(JSONObject prettyRespContent) {
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
                        .setSource(CrawlNewsHelper.parseSourceFromLink(
                                itemJson.optJSONObject("link").optString("href")
                        ));

                newsEntity.setCoverImageSrc(
                        CrawlNewsHelper.obtainCoverImageFromNews(newsEntity, itemJson)
                );
                newsEntities.add(newsEntity);
            }
        }

        return newsEntities;
    }
}
