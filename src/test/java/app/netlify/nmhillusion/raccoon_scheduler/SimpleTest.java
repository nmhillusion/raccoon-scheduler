package app.netlify.nmhillusion.raccoon_scheduler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static app.netlify.nmhillusion.n2mix.helper.log.LogHelper.getLogger;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * date: 2022-10-03
 * <p>
 * created-by: nmhillusion
 */

public class SimpleTest {
    @Test
    void testDateTimeFormatter() {
        final String formattedDateTime = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
        getLogger(this).info("formattedDateTime -> " + formattedDateTime);
        Assertions.assertNotNull(formattedDateTime);
        Assertions.assertTrue(0 < formattedDateTime.trim().length());

        Assertions.assertEquals(3, Math.ceil((float) 7 / 3));
    }

    @Test
    void testPurePatternMatching() {
        final Pattern pattern = Pattern.compile("12");
        final Matcher matcher = pattern.matcher("3 đám cưới đặc biệt của Vbiz trong tháng 12");

        assertTrue(matcher.find());
    }
}

