package app.netlify.nmhillusion.raccoon_scheduler.scheduler;

import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLog;

/**
 * date: 2022-12-06
 * <p>
 * created-by: nmhillusion
 */
public abstract class BaseScheduler {

    protected abstract BaseSchedulerService getBaseSchedulerService();

    public final void doExecute() {
        try {
            getLog(this).info("START JOB >>");
            getBaseSchedulerService().execute();
            getLog(this).info("<< END JOB");
        } catch (Exception ex) {
            getLog(this).error(ex);
        }
    }
}
