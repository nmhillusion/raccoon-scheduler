package tech.nmhillusion.raccoon_scheduler.scheduler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;
import tech.nmhillusion.raccoon_scheduler.service.CrawlNewsService;

import javax.annotation.PostConstruct;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

@ConditionalOnProperty(
        value = "service.crawl-news.enable"
)
@Component
public class CrawlNewsScheduler extends BaseScheduler {
    @Autowired
    private CrawlNewsService crawlNewsService;

    @Value("${service.crawl-news.cron-job}")
    private String cronJobValue;

    @PostConstruct
    private void init() {
        getLogger(this).info("Construct for " + getClass().getName());
        getLogger(this).info("cron job: " + cronJobValue);
    }

    @Override
    protected BaseSchedulerService getBaseSchedulerService() {
        return crawlNewsService;
    }

    @Scheduled(cron = "${service.crawl-news.cron-job}")
    public void execute() {
        doExecute();
    }

}
