package app.netlify.nmhillusion.raccoon_scheduler.helper;

import tech.nmhillusion.n2mix.constant.FileExtensionConstant;
import tech.nmhillusion.n2mix.validator.StringValidator;
import app.netlify.nmhillusion.raccoon_scheduler.entity.NewsEntity;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.lang.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Nullable
    public static String obtainCoverImageFromNews(NewsEntity newsEntity, @Nullable JSONObject itemJson) {
        String coverSrc = null;
        if (null != itemJson) {
            coverSrc = obtainImageSrcFromMediaContent(itemJson);

            if (StringValidator.isBlank(coverSrc)) {
                coverSrc = obtainImageSrcFromImageTag(itemJson);
            }
        }

        if (StringValidator.isBlank(coverSrc)) {
            coverSrc = obtainImageSrcFromDescription(newsEntity);
        }

        if (!StringValidator.isBlank(coverSrc)) {
            coverSrc = notZoomCoverImageByLink(coverSrc);
        }

        return coverSrc;
    }

    private static String obtainImageSrcFromImageTag(JSONObject itemJson) {
        final String rawImageValue = itemJson.optString("image");
        if (!StringValidator.isBlank(rawImageValue)) {
            return rawImageValue;
        }
        return null;
    }

    @Nullable
    private static String obtainImageSrcFromMediaContent(JSONObject itemJson) {
        final JSONObject mediaContent = itemJson.optJSONObject("media:content");
        if (null != mediaContent) {
            final String rawCoverUrl = mediaContent.optString("url");
            if (!StringValidator.isBlank(rawCoverUrl)) {
                final String stripRawCoverUrl = rawCoverUrl.contains("?") ? rawCoverUrl.substring(0, rawCoverUrl.indexOf("?")) : rawCoverUrl;
                if (Arrays.stream(FileExtensionConstant.IMAGE_EXTS).anyMatch(stripRawCoverUrl::endsWith)) {
                    return rawCoverUrl;
                }
            }
        }
        return null;
    }

    private static String notZoomCoverImageByLink(String coverSrc) {
        return coverSrc.replaceAll("/zoom/\\d+_\\d+/|/\\d+x\\d+/", "/")
                .replaceAll("/thumb_x\\d+x\\d+/", "/");
    }

    @Nullable
    private static String obtainImageSrcFromDescription(NewsEntity newsEntity) {
        final Pattern imgPattern = Pattern.compile("<img.+?src\\s*=\\s*['\"](.+?)['\"].*?>");
        final Matcher imgMatcher = imgPattern.matcher(newsEntity.getDescription());

        if (imgMatcher.find()) {
            return imgMatcher.group(1);
        }
        return null;
    }
}
