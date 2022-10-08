package app.netlify.nmhillusion.raccoon_scheduler.validator;

/**
 * date: 2022-10-08
 * <p>
 * created-by: nmhillusion
 */

public class StringValidator {
    public static boolean isBlank(String inp) {
        return null == inp || 0 == inp.trim().length();
    }
}
