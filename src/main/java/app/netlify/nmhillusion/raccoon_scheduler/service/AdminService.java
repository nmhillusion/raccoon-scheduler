package app.netlify.nmhillusion.raccoon_scheduler.service;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */

public interface AdminService {
    void reportError(String title, Throwable ex, Object... additionalInfo);
}
