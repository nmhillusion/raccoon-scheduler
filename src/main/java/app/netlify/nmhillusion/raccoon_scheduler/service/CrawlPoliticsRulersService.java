package app.netlify.nmhillusion.raccoon_scheduler.service;

import app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers.PendingUserEntity;

import java.util.List;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */
public interface CrawlPoliticsRulersService extends BaseSchedulerService {
    List<PendingUserEntity> getPendingUsers();
}
