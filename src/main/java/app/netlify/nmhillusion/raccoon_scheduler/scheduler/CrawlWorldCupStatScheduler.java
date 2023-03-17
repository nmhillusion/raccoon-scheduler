package app.netlify.nmhillusion.raccoon_scheduler.scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlWorldCupStatsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */
@ConditionalOnProperty(
        value = "service.crawl-world-cup-stats.enable"
)
@Component
public class CrawlWorldCupStatScheduler extends BaseScheduler {

    @Autowired
    private CrawlWorldCupStatsService crawlWorldCupStatsService;

    @Value("${service.crawl-world-cup-stats.cron-job}")
    private String cronJobValue;

    @PostConstruct
    private void init() {
        getLogger(this).info("Construct for " + getClass().getName());
        getLogger(this).info("cron job: " + cronJobValue);
    }

    @Override
    protected BaseSchedulerService getBaseSchedulerService() {
        return crawlWorldCupStatsService;
    }

    @Scheduled(cron = "${service.crawl-world-cup-stats.cron-job}")
    public void execute() {
        doExecute();
    }
}
