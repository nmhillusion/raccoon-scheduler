package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;
import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlWorldCupStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;


@SpringBootApplication
@EnableAutoConfiguration
@EnableScheduling
public class Application implements CommandLineRunner {

    @Autowired
    private CrawlNewsService crawlNewsService;
    @Autowired
    private CrawlPoliticsRulersService crawlPoliticsRulersService;

    @Autowired
    private CrawlWorldCupStatsService crawlWorldCupStatsService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    private void runCrawlNewsService() throws Throwable {
        crawlNewsService.execute();
    }

    private void runCrawlPoliticsRulersService() throws Throwable {
        crawlPoliticsRulersService.execute();
    }

    private void runCrawlWorldCupStatService() throws Throwable {
        crawlWorldCupStatsService.execute();
    }

    @Override
    public void run(String... args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("GMT+07:00")));
        getLog(this).info(":: Started App :: " + TimeZone.getDefault());
        try {
            FirebaseWrapper.setFirebaseConfig(FirebaseConfigConstant.getInstance().getFirebaseConfig());

            runCrawlNewsService();
            runCrawlPoliticsRulersService();
            runCrawlWorldCupStatService();
        } catch (Throwable ex) {
            LogHelper.getLog(this).error(ex);
        }
    }
}
