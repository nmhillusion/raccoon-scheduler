package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.n2mix.helper.YamlReader;
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

import java.io.IOException;
import java.io.InputStream;
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
