package app.netlify.nmhillusion.raccoon_scheduler.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StringUtilTest {

    @Test
    void trimWithNull() {
        assertEquals("", StringUtil.trimWithNull(null), "check with null");
        assertEquals("", StringUtil.trimWithNull(""), "check with empty");
        assertEquals("", StringUtil.trimWithNull("     "), "check with blank");
        assertEquals("abc", StringUtil.trimWithNull("     abc"), "check with blank start");
        assertEquals("abc", StringUtil.trimWithNull("abc     "), "check with blank end");
        assertEquals("abc", StringUtil.trimWithNull("  abc     "), "check with blank start and end");
        assertEquals("abc", StringUtil.trimWithNull("abc"), "check without blank");
    }

    @Test
    void removeHtmlTag() {
        assertEquals("", StringUtil.removeHtmlTag(null), "check with null");
        assertEquals("adb ", StringUtil.removeHtmlTag("adb "), "check with no tag");
        assertEquals(" one ", StringUtil.removeHtmlTag(" <b>one "), "check with 1 tag");
        assertEquals("one two", StringUtil.removeHtmlTag("<i>one</i> two"), "check with 2 tag");
        assertEquals(" one two three", StringUtil.removeHtmlTag("<hr> one <b>two</b> <img src='https://google.com' />three"), "check with 4 tag");
    }
}