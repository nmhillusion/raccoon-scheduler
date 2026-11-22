package tech.nmhillusion.raccoon_scheduler.helper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Collects crawl errors as they happen across concurrent source tasks, so they
 * can be written to Firestore once at the end of a run.
 *
 * created date: 2026-11-22
 */
public final class CrawlErrorCollector {
    private final Queue<Map<String, Object>> errors = new ConcurrentLinkedQueue<>();

    public void add(Map<String, Object> error) {
        errors.add(Collections.unmodifiableMap(new LinkedHashMap<>(error)));
    }

    public List<Map<String, Object>> snapshot() {
        return List.copyOf(errors);
    }

    public void clear() {
        errors.clear();
    }
}
