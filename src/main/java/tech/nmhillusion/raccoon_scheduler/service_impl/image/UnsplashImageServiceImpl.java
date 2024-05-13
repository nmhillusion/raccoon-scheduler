package tech.nmhillusion.raccoon_scheduler.service_impl.image;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import tech.nmhillusion.n2mix.constant.OkHttpContentType;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.helper.log.LogHelper;
import tech.nmhillusion.n2mix.type.ChainMap;
import tech.nmhillusion.raccoon_scheduler.entity.image.unsplash.UnsplashImageEntity;
import tech.nmhillusion.raccoon_scheduler.helper.OnRenderHelper;
import tech.nmhillusion.raccoon_scheduler.service.image.UnsplashImageService;
import tech.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-04-16
 */
@Service
public class UnsplashImageServiceImpl extends BaseSchedulerServiceImpl implements UnsplashImageService {
    private CredentialConfig credential;
    private RandomConfig randomConfig;
    private StoreServerConfig storeServerConfig;

    public UnsplashImageServiceImpl() throws IOException {
        init();
    }

    private void init() throws IOException {
        {
            /// Mark: LOAD CREDENTIAL
            credential = new CredentialConfig(
                    getConfig("credential.host", String.class),
                    getConfig("credential.accessToken", String.class)
            );
        }

        {
            /// Mark: LOAD RANDOM CONFIG
            randomConfig = new RandomConfig(
                    getConfig("random.queryText", String.class)
                    , String.valueOf(getConfig("random.count", int.class))
                    , getConfig("random.orientation", String.class)
            );
        }

        {
            /// Mark: LOAD STORAGE SERVER CONFIG
            this.storeServerConfig = new StoreServerConfig(
                    getConfig("storageServer.host", String.class)
                    , getConfig("storageServer.endpoint", String.class)
                    , getConfig("storageServer.method", String.class)
                    , getConfig("storageServer.fileFieldName", String.class)
                    , getConfig("storageServer.fileInfoFieldName", String.class)
                    , getConfig("storageServer.token.clientId", String.class)
                    , getConfig("storageServer.token.clientSecret", String.class)
            );

            LogHelper.getLogger(this).info("storeServerConfig = %s".formatted(this.storeServerConfig));
        }
    }

    private <T> T getConfig(String configKey, Class<T> class2Cast) throws IOException {
        try (final InputStream configStream_ = getClass().getClassLoader().getResourceAsStream("service-config/image/unsplash.yml")) {
            return new YamlReader(configStream_).getProperty(configKey, class2Cast);
        }
    }

    @Override
    public boolean isEnableExecution() {
        return true;
    }

    @Override
    public UnsplashImageEntity getRandomImageFromUnsplash() throws Exception {
        final HttpHelper httpHelper = new HttpHelper();
        final byte[] rawData = httpHelper.get(
                new RequestHttpBuilder()
                        .setUrl(
                                "{{host}}/photos/random".replace("{{host}}", credential.host())
                        )
                        .addParam("client_id", credential.accessToken())
                        .addParam("query", randomConfig.queryText())
                        .addParam("orientation", randomConfig.orientation())
                        .addParam("count", randomConfig.count())
        );

        final JSONArray searchResult = new JSONArray(new String(rawData));
        final UnsplashImageEntity imageEntity = parseSearchResultToImageEntity(
                searchResult.getJSONObject(0)
        );

        LogHelper.getLogger(this).info("imageEntity = %s".formatted(imageEntity.toString()));

        return imageEntity;
    }

    @Override
    public void doExecute() throws Throwable {
        final UnsplashImageEntity imageEntity = getRandomImageFromUnsplash();

        final byte[] imageData = new HttpHelper().get(
                new RequestHttpBuilder()
                        .setUrl(imageEntity.getDownloadFullUrl())
        );

        postToStorageServer(imageEntity, imageData);
    }

    private UnsplashImageEntity parseSearchResultToImageEntity(JSONObject searchResult) {
        return new UnsplashImageEntity()
                .setDescription(
                        searchResult.optString("description")
                )
                .setColor(
                        searchResult.optString("color")
                )
                .setHeight(
                        searchResult.optInt("height")
                )
                .setWidth(
                        searchResult.optInt("width")
                )
                .setId(
                        searchResult.optString("id")
                )
                .setHtmlLink(
                        searchResult.optJSONObject("links")
                                .optString("html")
                )
                .setDownloadFullUrl(
                        searchResult.optJSONObject("urls")
                                .optString("full")
                )
                .setUpdateStorageServerTime(
                        ZonedDateTime.now()
                )
                ;
    }

    @Override
    public String postToStorageServer(UnsplashImageEntity imageEntity, byte[] imageData) throws Throwable {
        LogHelper.getLogger(this).info("postToStorageBucket::imageEntity = %s".formatted(imageEntity.toString()));

        if (!new OnRenderHelper().wakeup()) {
            throw new Exception("wakeup onRender server failed");
        } else {
            LogHelper.getLogger(this).info("wakeup onRender server success");
        }

        final byte[] resultResponse = new HttpHelper().httpExecute(new RequestHttpBuilder()
                        .setUrl(
                                "{{host}}{{endpoint}}"
                                        .replace("{{host}}", storeServerConfig.host())
                                        .replace("{{endpoint}}", storeServerConfig.endpoint())
                        )
                        .addHeader("client-id", storeServerConfig.tokenClientId())
                        .addHeader("client-secret", storeServerConfig.tokenClientSecret())
                        .setBody(new ChainMap<String, Object>()
                                .chainPut(storeServerConfig.fileInfoFieldName(), new JSONObject(imageEntity))
                                .chainPut(storeServerConfig.fileFieldName(), imageData), OkHttpContentType.MULTIPART_FORM_DATA)
                , HttpMethod.valueOf(storeServerConfig.method())
                , false);

        final String responseInText = new String(resultResponse);
        LogHelper.getLogger(this).info("postToStorageBucket::resultResponse = %s".formatted(responseInText));

        return responseInText;
    }

    record CredentialConfig(String host, String accessToken) {
    }

    record RandomConfig(String queryText,
                        String count,
                        String orientation) {
    }

    record StoreServerConfig(String host, String endpoint, String method, String fileFieldName,
                             String fileInfoFieldName, String tokenClientId, String tokenClientSecret) {
    }
}
