package tech.nmhillusion.raccoon_scheduler.service_impl.image;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.firebase.cloud.StorageClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import tech.nmhillusion.n2mix.helper.http.HttpHelper;
import tech.nmhillusion.n2mix.helper.http.RequestHttpBuilder;
import tech.nmhillusion.n2mix.helper.log.LogHelper;
import tech.nmhillusion.n2mix.util.IOStreamUtil;
import tech.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import tech.nmhillusion.raccoon_scheduler.entity.image.unsplash.UnsplashImageEntity;
import tech.nmhillusion.raccoon_scheduler.service.image.UnsplashImageService;
import tech.nmhillusion.raccoon_scheduler.service_impl.BaseSchedulerServiceImpl;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-04-16
 */
@Service
public class UnsplashImageServiceImpl extends BaseSchedulerServiceImpl implements UnsplashImageService {
    private CredentialConfig credential;
    private RandomConfig randomConfig;

    @PostConstruct
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
    public void doExecute() throws Throwable {
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

        postInfoToFireStore(imageEntity);

//        final byte[] imageData = httpHelper.get(
//                new RequestHttpBuilder()
//                        .setUrl(imageEntity.getDownloadFullUrl())
//        );
        final byte[] imageData = IOStreamUtil.convertInputStreamToByteArray(
                getClass().getClassLoader().getResourceAsStream("test-data/image/hd.jpg")
        );

        postToStorageBucket(imageData);
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
                ;
    }

    private void postToStorageBucket(byte[] imageData) throws Throwable {
        FirebaseWrapper.getInstance()
                .runWithWrapper(firebaseHelper -> {
                    final Optional<StorageClient> storageOpt = firebaseHelper.getStorage();

                    if (storageOpt.isEmpty()) {
                        throw new IOException("Fail to acquire storage");
                    }

                    final StorageClient storageClient = storageOpt.get();
                    final Bucket defaultBucket_ = storageClient.bucket(
                            FirebaseConfigConstant
                                    .getInstance()
                                    .getConfig("service-account.storageBucketName", String.class)
                    );

                    final Blob blob_ = defaultBucket_.create(
                            "hd.jpg",
                            imageData,
                            "image/jpg"
                    );

                    final String downloadLink = blob_.asBlobInfo().getMediaLink();

                    LogHelper.getLogger(this).info("push storageBucket::blob = %s".formatted(blob_));
                    LogHelper.getLogger(this).info("push storageBucket::downloadLink = %s".formatted(downloadLink));
                });
    }

    private void postInfoToFireStore(UnsplashImageEntity imageEntity) throws Throwable {
        FirebaseWrapper.getInstance()
                .runWithWrapper(firebaseHelper -> {
                    LogHelper.getLogger(this).info("push firestore::image = %s".formatted(imageEntity));

                    final Optional<Firestore> firestoreOpt = firebaseHelper.getFirestore();

                    if (firestoreOpt.isEmpty()) {
                        throw new IOException("Fail to acquire firestore");
                    }

                    final Firestore firestore_ = firestoreOpt.get();
                    final CollectionReference collection = firestore_.collection("raccoon-scheduler--image-unsplash");
                    final ApiFuture<WriteResult> currentPostResult = collection.document("current")
                            .set(imageEntity, SetOptions.merge());

                    final WriteResult currentInfoResult = currentPostResult.get();

                    LogHelper.getLogger(this).info("push firestore::currentInfoResult = %s".formatted(currentInfoResult));
                });
    }

    record CredentialConfig(String host, String accessToken) {
    }

    record RandomConfig(String queryText,
                        String count,
                        String orientation) {
    }
}
