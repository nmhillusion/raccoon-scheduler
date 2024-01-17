package app.netlify.nmhillusion.raccoon_scheduler.scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-12-06
 * <p>
 * created-by: nmhillusion
 */
public abstract class BaseScheduler {

    protected abstract BaseSchedulerService getBaseSchedulerService();

    public final void doExecute() {
        try {
            getLogger(this).info("START JOB >>");
            getBaseSchedulerService().execute();
            getLogger(this).info("<< END JOB");
        } catch (Throwable ex) {
            getLogger(this).error(ex);
        }
    }
}
