package tech.nmhillusion.raccoon_scheduler.service_impl.euro_cup_stat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.nmhillusion.n2mix.util.IOStreamUtil;
import tech.nmhillusion.raccoon_scheduler.entity.world_cup_stat.MatchStatEntity;
import tech.nmhillusion.raccoon_scheduler.service.CrawlEuroCupStatsService;

import java.io.InputStream;
import java.util.List;

import static tech.nmhillusion.n2mix.helper.log.LogHelper.getLogger;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-05-11
 */
class CrawlEuroCupStatsServiceImplTest {

    @Test
    void testBuildMatchListFromStatPageContent() {
        final List<MatchStatEntity> matchStatEntityList = Assertions.assertDoesNotThrow(() -> {
            try (final InputStream pageContentStream = getClass().getClassLoader().getResourceAsStream("test-data/euro-cup-stat/stat.html")) {
                final CrawlEuroCupStatsService service = new CrawlEuroCupStatsServiceImpl();

                final String pageContent = IOStreamUtil.convertInputStreamToString(pageContentStream);

                return service.buildMatchListFromStatPageContent(pageContent);
            }
        });

        Assertions.assertNotNull(matchStatEntityList);
        Assertions.assertFalse(matchStatEntityList.isEmpty());

        for (final MatchStatEntity matchStatEntity : matchStatEntityList) {
            getLogger(this).info("match stat: " + matchStatEntity);
        }

        getLogger(this).info("matches length: " + matchStatEntityList.size());
    }
}