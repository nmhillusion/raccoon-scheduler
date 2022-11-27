package app.netlify.nmhillusion.raccoon_scheduler.service_impl;

import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

public abstract class BaseSchedulerServiceImpl implements BaseSchedulerService {
    public abstract boolean isEnableExecution();

    public final void execute() throws Exception {
        if (!isEnableExecution()) {
            LogHelper.getLog(this).warn("NOT enable to running this service");
        } else {
            doExecute();
        }
    }

    public abstract void doExecute() throws Exception;
}
