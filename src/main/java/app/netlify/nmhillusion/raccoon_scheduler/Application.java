package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.config.FirebaseConfigConstant;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlWorldCupStatsService;
import app.netlify.nmhillusion.raccoon_scheduler.service.politics.CrawlPoliticsRulersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import tech.nmhillusion.n2mix.helper.YamlReader;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseConfig;
import tech.nmhillusion.n2mix.helper.firebase.FirebaseWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.TimeZone;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

@SpringBootApplication
@EnableAutoConfiguration(
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class
        }
)
@EnableScheduling
public class Application implements CommandLineRunner {


    @Autowired
    private CrawlNewsService crawlNewsService;
    @Autowired
    private CrawlPoliticsRulersService crawlPoliticsRulersService;
    @Autowired
    private CrawlWorldCupStatsService crawlWorldCupStatsService;

    public static void main(String[] args) throws IOException {
        final String APP_ZONE_ID = getAppConfig("time-zone", "GMT+07:00");
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(APP_ZONE_ID)));
        SpringApplication.run(Application.class, args);
    }

    private static String getAppConfig(String configKey, String default_) throws IOException {
        try (final InputStream appConfigStream = Application.class.getClassLoader().getResourceAsStream("application.yml")) {
            return new YamlReader(appConfigStream).getProperty(configKey, String.class, default_);
        }
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
        getLogger(this).info(":: Started App :: " + TimeZone.getDefault());
        try {
            final FirebaseConfig firebaseConfig = FirebaseConfigConstant.getInstance().getFirebaseConfig();
            FirebaseWrapper.setFirebaseConfig(firebaseConfig);

            runCrawlNewsService();
//            runCrawlPoliticsRulersService();
//            runCrawlWorldCupStatService();
        } catch (Throwable ex) {
            getLogger(this).error(ex);
        }
    }
}
