package app.netlify.nmhillusion.raccoon_scheduler.scheduler.politics;

import app.netlify.nmhillusion.raccoon_scheduler.scheduler.BaseScheduler;
import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;
import app.netlify.nmhillusion.raccoon_scheduler.service.politics.CrawlPoliticsRulersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */
@ConditionalOnProperty(
        value = "service.politics.crawl-politics-rulers.enable"
)
@Component
public class CrawlPoliticsRulersScheduler extends BaseScheduler {
    @Autowired
    private CrawlPoliticsRulersService crawlPoliticsRulersService;

    @Value("${service.crawl-politics-rulers.cron-job}")
    private String cronJobValue;

    @PostConstruct
    private void init() {
        getLog(this).info("Construct for " + getClass().getName());
        getLog(this).info("cron job: " + cronJobValue);
    }

    @Override
    protected BaseSchedulerService getBaseSchedulerService() {
        return crawlPoliticsRulersService;
    }

    @Scheduled(cron = "${service.politics.crawl-politics-rulers.cron-job}")
    public void execute() {
        doExecute();
    }
}
