package app.netlify.nmhillusion.raccoon_scheduler.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

public class LogHelper {
    public static Logger getLog(Object object) {
        if (object instanceof Class<?>) {
            return LoggerFactory.getLogger((Class<?>) object);
        } else if (object instanceof String) {
            return LoggerFactory.getLogger((String) object);
        } else if (null != object) {
            return LoggerFactory.getLogger(object.getClass());
        } else {
            return LoggerFactory.getLogger(LogHelper.class);
        }
    }
}
