package app.netlify.nmhillusion.raccoon_scheduler.helper.firebase;

import app.netlify.nmhillusion.raccoon_scheduler.util.YamlReader;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */
public class FirebaseHelper implements AutoCloseable {
    private final FirebaseApp firebaseApp;

    public FirebaseHelper() throws IOException {
        try (final InputStream firebaseConfig = getClass().getClassLoader().getResourceAsStream("app-config/firebase.yml")) {
            final YamlReader yamlReader = new YamlReader(firebaseConfig);
            final String serviceAccountPath = yamlReader.getProperty("service-account.path", String.class, "");
            final String serviceAccountProjectId = yamlReader.getProperty("service-account.project-id", String.class, "");

            getLog(this).info("==> serviceAccountPath = {}", serviceAccountPath);
            getLog(this).info("==> serviceAccountProjectId = {}", serviceAccountProjectId);

            getLog(this).info("Initializing Firebase >>");
            try (final InputStream serviceAccInputStream = Files.newInputStream(Path.of(serviceAccountPath))) {
                final FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccInputStream))
                        .setProjectId(serviceAccountProjectId)
                        .build();
                firebaseApp = FirebaseApp.initializeApp(options);
                getLog(this).info("<< Initializing Firebase Success: " + firebaseApp);
            }
        }
    }

    public Firestore getFirestore() throws IOException {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @Override
    public void close() throws Exception {
        getFirestore().close();
        getFirestore().shutdown();

        firebaseApp.delete();
    }
}
