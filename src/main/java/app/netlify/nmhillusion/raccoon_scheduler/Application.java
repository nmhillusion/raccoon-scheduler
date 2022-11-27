package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlWorldCupStatsService;
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

    @Autowired
    private CrawlWorldCupStatsService crawlWorldCupStatsService;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    private void runCrawlNewsService() throws Exception {
        crawlNewsService.execute();
    }

    private void runCrawlPoliticsRulersService() throws Exception {
        crawlPoliticsRulersService.execute();
    }

    private void runCrawlWorldCupStatService() throws Exception {
        crawlWorldCupStatsService.execute();
    }

    @Override
    public void run(String... args) throws Exception {
        getLog(this).info(":: Started App ::");
        runCrawlNewsService();
        runCrawlPoliticsRulersService();
        runCrawlWorldCupStatService();
    }
}
