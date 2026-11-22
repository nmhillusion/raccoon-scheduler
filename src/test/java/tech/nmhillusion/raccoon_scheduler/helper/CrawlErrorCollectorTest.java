package tech.nmhillusion.raccoon_scheduler.helper;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrawlErrorCollectorTest {
    private static Map<String, Object> error(String sourceKey, String message) {
        final Map<String, Object> error = new LinkedHashMap<>();
        error.put("sourceKey", sourceKey);
        error.put("errorMessage", message);
        error.put("stackTrace", "sample stack trace");
        error.put("timestamp", "2026-11-22T06:00:00.000+07:00");
        return error;
    }

    @Test
    void collectsAllErrorsForTheRun() {
        final CrawlErrorCollector collector = new CrawlErrorCollector();
        collector.add(error("vnexpress", "first failure"));
        collector.add(error("soha", "second failure"));

        final List<Map<String, Object>> errors = collector.snapshot();

        assertEquals(2, errors.size());
        assertEquals("vnexpress", errors.get(0).get("sourceKey"));
        assertEquals("soha", errors.get(1).get("sourceKey"));
    }

    @Test
    void snapshotDoesNotChangeWhenLaterErrorsAreAdded() {
        final CrawlErrorCollector collector = new CrawlErrorCollector();
        collector.add(error("vnexpress", "first failure"));
        final List<Map<String, Object>> snapshot = collector.snapshot();

        collector.add(error("soha", "second failure"));

        assertEquals(1, snapshot.size());
    }

    @Test
    void clearRemovesErrorsFromPreviousRun() {
        final CrawlErrorCollector collector = new CrawlErrorCollector();
        collector.add(error("vnexpress", "previous run failure"));

        collector.clear();

        assertEquals(0, collector.snapshot().size());
    }
}
