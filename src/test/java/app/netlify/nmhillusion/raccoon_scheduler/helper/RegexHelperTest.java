package app.netlify.nmhillusion.raccoon_scheduler.helper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static app.netlify.nmhillusion.raccoon_scheduler.helper.LogHelper.getLog;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class RegexHelperTest {

    @Autowired
    private RegexHelper regexHelper;

    @Test
    void parse() {
        final List<List<String>> actualList = regexHelper.parse("a-b-c-d", "(?:-?(\\w))", Pattern.CASE_INSENSITIVE);
        assertEquals(4, actualList.size(), "size of matcher");

        final List<List<String>> expectedList = new ArrayList<>();
        {
            expectedList.add(List.of("a", "a"));
            expectedList.add(List.of("-b", "b"));
            expectedList.add(List.of("-c", "c"));
            expectedList.add(List.of("-d", "d"));
        }

        assertEquals(expectedList, actualList);

        getLog(this).info("actualList: " + actualList);
    }
}