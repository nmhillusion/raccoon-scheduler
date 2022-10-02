package app.netlify.nmhillusion.raccoon_scheduler.helper.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
@Component
public class FirebaseHelper {
    private FirebaseApp firebaseApp;

    @Value("${firebase.service-account.path}")
    private String serviceAccountPath;

    private synchronized void initialize() throws IOException {
        if (null == firebaseApp) {
            getLog(this).info("Initializing Firebase >>");
            try (final InputStream serviceAccInputStream = Files.newInputStream(Path.of(serviceAccountPath))) {
                final FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccInputStream))
                        .setProjectId("nmhillusion-service")
                        .build();
                firebaseApp = FirebaseApp.initializeApp(options);
                getLog(this).info("<< Initializing Firebase Success: " + firebaseApp);
            }
        }
    }

    public Firestore getFirestore() throws IOException {
        initialize();
        return FirestoreClient.getFirestore(firebaseApp);
    }
}
