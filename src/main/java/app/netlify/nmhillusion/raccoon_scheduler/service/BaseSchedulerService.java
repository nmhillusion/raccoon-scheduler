package app.netlify.nmhillusion.raccoon_scheduler.service;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */
public interface BaseSchedulerService {
    boolean isEnableExecution();

    void execute() throws Throwable;

    void doExecute() throws Throwable;
}
