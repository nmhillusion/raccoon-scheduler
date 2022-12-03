package app.netlify.nmhillusion.raccoon_scheduler;

import app.netlify.nmhillusion.n2mix.helper.log.LogHelper;
import app.netlify.nmhillusion.n2mix.util.IOStreamUtil;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * date: 2022-12-03
 * <p>
 * created-by: nmhillusion
 */

public class FlagParserTest {
    @Test
    void obtainCountryFlags() {
        assertDoesNotThrow(() -> {
            try (final InputStream srCssStream = getClass().getClassLoader().getResourceAsStream("test-data/world-cup-stat/sr-min.css")) {
                if (null != srCssStream) {
                    final Map<String, String> flagData = new HashMap<>();
                    final String srCssContent = IOStreamUtil.convertInputStreamToString(srCssStream);
                    final Pattern cssPattern = Pattern.compile(".f-([\\w-]+)\\{background-image:url\\((.+?)\\)}", Pattern.CASE_INSENSITIVE);
                    final Matcher matcher = cssPattern.matcher(srCssContent);
                    while (matcher.find()) {
                        final String countryShortName = matcher.group(1);
                        final String flagPath = matcher.group(2);
                        LogHelper.getLog(this).infoFormat(">> mapping {%s} -> %s", countryShortName, flagPath);

                        flagData.put(countryShortName, flagPath);
                    }
                    final String flagCssContent = buildingFlagCssContent(flagData);

                    try (OutputStream os = new FileOutputStream("flag-country.css")) {
                        os.write(flagCssContent.getBytes());
                        os.flush();
                    }
                }
            }
        });

    }

    private String buildingFlagCssContent(Map<String, String> flagMap) {
        final StringBuilder cssBuilder = new StringBuilder();

        for (String countryFlagName : flagMap.keySet()) {
            cssBuilder
                    .append(".flag.flag-country-").append(countryFlagName)
                    .append("{")
                    .append("background-image:url('")
                    .append(flagMap.get(countryFlagName))
                    .append("')")
                    .append("}")
            ;
        }

        return cssBuilder.toString();
    }
}
