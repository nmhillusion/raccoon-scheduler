package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.helper.firebase.FirebaseHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

@SpringBootApplication
@EnableAutoConfiguration
@EnableScheduling
public class Application implements CommandLineRunner {

    @Autowired
    private CrawlNewsService crawlNewsService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        getLog(this).info(":: Started App ::");
        if (FirebaseHelper.isEnable()) {
            try (FirebaseHelper firebaseHelper = new FirebaseHelper()) {
                firebaseHelper.getFirestore().listCollections().forEach(col -> {
                    LogHelper.getLog(this).info("collection -> " + col.getId());
                });
            }

            crawlNewsService.execute();
        } else {
            getLog(this).warn("Firebase is not enable");
        }
    }
}
