package app.netlify.nmhillusion.raccoon_scheduler.helper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

public class CrawlNewsHelper {

    @Nullable
    public static <T> T getItemAt0(List<T> items) {
        if (null == items || items.isEmpty()) {
            return null;
        }

        return items.get(0);
    }

    @Nullable
    public static <T> T getItemAt0(JSONArray items, Class<T> classOfItem) {
        if (null == items || items.isEmpty()) {
            return null;
        }

        return classOfItem.cast(items.get(0));
    }

    @Nullable
    public static String prettierDescription(String description) {
        if (null == description) {
            return null;
        }

        return description.replaceAll("/zoom/\\d+_\\d+/", "");
    }

    @Nullable
    public static String parseLinkFromFeed(Object link) {
        if (null == link) {
            return null;
        }

        if (link instanceof final JSONObject linkJson) {
            if (linkJson.has("$")) {
                return linkJson.getJSONObject("$").getString("href");
            }
        } else if (link instanceof String) {
            return String.valueOf(link);
        }
        return null;
    }

    public static String parseSourceFromLink(String link) {
        if (null == link) {
            return null;
        }

        link = link.replaceFirst("https?://", "");
        link = link.substring(0, link.indexOf("/"));
        return link;
    }
}
