package app.netlify.nmhillusion.raccoon_scheduler.scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.service.CrawlNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

@Component
public class CrawlNewsScheduler {
    @Autowired
    private CrawlNewsService crawlNewsService;

    @Scheduled(fixedRate = 600_000)
    public void execute() {
        try {
            getLog(this).info("START JOB >>");
            crawlNewsService.execute();
            getLog(this).info("<< END JOB");
        } catch (Exception ex) {
            getLog(this).error(ex.getMessage(), ex);
        }
    }

}
