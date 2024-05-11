package tech.nmhillusion.raccoon_scheduler.service;

import tech.nmhillusion.n2mix.exception.GeneralException;
import tech.nmhillusion.raccoon_scheduler.entity.world_cup_stat.MatchStatEntity;

import java.util.List;

/**
 * date: 2022-11-27
 * <p>
 * created-by: nmhillusion
 */
public interface CrawlEuroCupStatsService extends BaseSchedulerService {
    List<MatchStatEntity> buildMatchListFromStatPageContent(String statPageContent) throws GeneralException;
}
