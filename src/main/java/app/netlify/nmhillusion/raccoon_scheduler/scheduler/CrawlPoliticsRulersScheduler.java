package app.netlify.nmhillusion.raccoon_scheduler.scheduler;

import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.n2mix.util.ExceptionUtil;
import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlPoliticsRulersService;
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
        value = "service.crawl-politics-rulers.enable"
)
@Component
public class CrawlPoliticsRulersScheduler {
    @Autowired
    private CrawlPoliticsRulersService crawlPoliticsRulersService;

    @Value("${service.crawl-politics-rulers.cron-job}")
    private String cronJobValue;

    @PostConstruct
    private void init() {
        getLog(this).info("Construct for " + getClass().getName());
        getLog(this).info("cron job: " + cronJobValue);
    }

    @Scheduled(cron = "${service.crawl-politics-rulers.cron-job}")
    public void execute() {
        try {
            getLog(this).info("START JOB >>");
            crawlPoliticsRulersService.execute();
            getLog(this).info("<< END JOB");
        } catch (Exception ex) {
            getLog(this).error(ex);
            throw ExceptionUtil.throwException(ex);
        }
    }
}
