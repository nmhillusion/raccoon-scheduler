package app.netlify.nmhillusion.raccoon_scheduler.util;

import org.jetbrains.annotations.NotNull;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * date: 2022-10-05
 * <p>
 * created-by: nmhillusion
 */

public class YamlReader {

    private final List<PropertySource<?>> propertySources;

    public YamlReader(InputStream inputStream) throws IOException {

        this.propertySources = new YamlPropertySourceLoader().load("data", new AbstractResource() {
            @NotNull
            @Override
            public String getDescription() {
                return "no description";
            }

            @NotNull
            @Override
            public InputStream getInputStream() throws IOException {
                return inputStream;
            }
        });
    }

    public List<PropertySource<?>> getPropertySources() {
        return propertySources;
    }

    /**
     * @param key          key of property to obtain from source
     * @param classToCast  class to cast for object
     * @param <T>          type will be returned
     * @param defaultValue value will be return if not found
     * @return if missing key or cannot cast will return <b>defaultValue</b>
     */
    public <T> T getProperty(String key, Class<T> classToCast, T defaultValue) {
        final Optional<PropertySource<?>> propertySource = propertySources.stream().filter(prop -> prop.containsProperty(key)).findFirst();
        if (propertySource.isPresent()) {
            final Object property = propertySource.get().getProperty(key);
            if (classToCast.isInstance(property)) {
                return classToCast.cast(property);
            }
        }
        return defaultValue;
    }

    /**
     * @param key         key of property to obtain from source
     * @param classToCast class to cast for object
     * @param <T>         type will be returned
     * @return if missing key or cannot cast will return `null`
     */
    public <T> T getProperty(String key, Class<T> classToCast) {
        return getProperty(key, classToCast, null);
    }
}
