package app.netlify.nmhillusion.raccoon_scheduler.util;

import app.netlify.nmhillusion.raccoon_scheduler.validator.StringValidator;
import org.springframework.lang.Nullable;

/**
 * date: 2022-11-19
 * <p>
 * created-by: nmhillusion
 */

public class StringUtil {
    public static String trimWithNull(@Nullable String input) {
        if (StringValidator.isBlank(input)) {
            return "";
        }
        return input.trim();
    }

    public static String removeHtmlTag(@Nullable String input) {
        input = null == input ? "" : input;

        input = input.replaceAll("</?.+?>", "");

        return input;
    }
}
