package tech.nmhillusion.raccoon_scheduler.service_impl;

import tech.nmhillusion.n2mix.helper.log.LogHelper;
import tech.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

public abstract class BaseSchedulerServiceImpl implements BaseSchedulerService {
    public abstract boolean isEnableExecution();

    public final void execute() throws Throwable {
        if (!isEnableExecution()) {
            getLogger(this).warn("NOT enable to running this service");
        } else {
            LogHelper.getLogger(this).info(">> start executing for class " + getClass().getSimpleName());
            doExecute();
        }
    }

    public abstract void doExecute() throws Throwable;
}
