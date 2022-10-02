package app.netlify.nmhillusion.raccoon_scheduler.type;

import app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

public class Stringeable implements Serializable {
    @Override
    public String toString() {
        final Map<String, Object> fieldMap = new HashMap<>();

        final Field[] fields = getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                fieldMap.put(field.getName(), field.get(this));
            } catch (IllegalAccessException e) {
                LogHelper.getLog(this).error(e.getMessage(), e);
                fieldMap.put(field.getName(), "!!!Error: IllegalAccessException");
            }
        }

        return """
                class $className
                    $fields
                """
                .replace("$className", getClass().getName())
                .replace("$fields", fieldMap.toString());
    }
}
