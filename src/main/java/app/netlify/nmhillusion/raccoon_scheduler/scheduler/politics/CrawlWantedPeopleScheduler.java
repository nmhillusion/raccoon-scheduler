package app.netlify.nmhillusion.raccoon_scheduler.scheduler.politics;

import app.netlify.nmhillusion.raccoon_scheduler.scheduler.BaseScheduler;
import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;
import app.netlify.nmhillusion.raccoon_scheduler.service.politics.CrawlWantedPeopleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2023-02-12
 * <p>
 * created-by: nmhillusion
 */
@ConditionalOnProperty(
        value = "service.politics.crawl-wanted-people.enable"
)
@Component
public class CrawlWantedPeopleScheduler extends BaseScheduler {

    @Autowired
    private CrawlWantedPeopleService crawlWantedPeopleService;

    @Value("${service.politics.crawl-wanted-people.cron-job}")
    private String cronJobValue;

    @PostConstruct
    private void init() {
        getLog(this).info("Construct for " + getClass().getName());
        getLog(this).info("cron job: " + cronJobValue);
    }

    @Override
    protected BaseSchedulerService getBaseSchedulerService() {
        return crawlWantedPeopleService;
    }

    @Scheduled(cron = "${service.politics.crawl-wanted-people.cron-job}")
    public void execute() {
        doExecute();
    }
}
