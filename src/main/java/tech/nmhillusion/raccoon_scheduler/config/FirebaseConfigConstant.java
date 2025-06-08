package tech.nmhillusion.raccoon_scheduler.config;

import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseConfig;

import java.io.InputStream;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-25
 * <p>
 * created-by: nmhillusion
 */

public class FirebaseConfigConstant {
    private static final FirebaseConfigConstant instance = new FirebaseConfigConstant();
    private final FirebaseConfig firebaseConfig = new FirebaseConfig();

    private FirebaseConfigConstant() {
        final boolean configEnable = getConfig("config.enable", boolean.class);
        final String projectId = getConfig("service-account.project-id", String.class);
        final String credentialFilePath = getConfig("service-account.path", String.class);

        getLogger(this).info("configEnable: " + configEnable);
        getLogger(this).info("projectId: " + projectId);
        getLogger(this).info("credentialFilePath: " + credentialFilePath);

        firebaseConfig
                .setEnable(configEnable)
                .setServiceAccountConfig(new FirebaseConfig.ServiceAccountConfig()
                        .setProjectId(projectId)
                        .setCredentialFilePath(credentialFilePath)
                );
    }

    public static FirebaseConfigConstant getInstance() {
        return instance;
    }

    public <T> T getConfig(String key, Class<T> classToObtain) {
        try {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("app-config/firebase.yml")) {
                return new YamlReader(is).getProperty(key, classToObtain);
            }
        } catch (Exception ex) {
            getLogger(this).error(ex);
            return null;
        }
    }

    public FirebaseConfig getFirebaseConfig() {
        return firebaseConfig;
    }
}
