package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.n2mix.helper.firebase.FirebaseHelper;
import app.netlify.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;


@SpringBootApplication
@EnableAutoConfiguration
@EnableScheduling
public class Application implements CommandLineRunner {

    @Autowired
    private CrawlNewsService crawlNewsService;
    @Autowired
    private CrawlPoliticsRulersService crawlPoliticsRulersService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    private void runCrawlNewsService() throws Exception {
        try (final FirebaseHelper firebaseHelper = new FirebaseHelper(FirebaseConfigConstant.getInstance().getFirebaseConfig())) {
            if (firebaseHelper.isEnable()) {
                firebaseHelper
                        .getFirestore().ifPresent(_fs ->
                                _fs.listCollections().forEach(col -> {
                                    getLog(this).info("collection -> " + col.getId());
                                })
                        );
                crawlNewsService.execute();
            } else {
                getLog(this).warn("Firebase is not enable");

                crawlNewsService.execute();
            }
        }
    }

    private void runCrawlPoliticsRulersService() throws Exception {
        crawlPoliticsRulersService.execute();
    }

    @Override
    public void run(String... args) throws Exception {
        getLog(this).info(":: Started App ::");
//        runCrawlNewsService();
        runCrawlPoliticsRulersService();
    }
}
