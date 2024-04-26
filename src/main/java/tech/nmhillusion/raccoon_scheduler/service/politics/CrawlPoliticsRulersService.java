package tech.nmhillusion.raccoon_scheduler.service.politics;

import tech.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers.PendingUserEntity;
import tech.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

import java.util.List;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */
public interface CrawlPoliticsRulersService extends BaseSchedulerService {
    List<PendingUserEntity> getPendingUsers();
}
