package tech.nmhillusion.raccoon_scheduler.helper;

import org.junit.jupiter.api.Test;
import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NewsItemDocumentMapperTest {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final ZonedDateTime CRAWLED_AT =
            ZonedDateTime.of(2026, 11, 15, 6, 0, 0, 0, ZoneOffset.ofHours(7));

    private static NewsEntity sampleNews() {
        return new NewsEntity()
                .setTitle("Giá xăng dầu có biến động")
                .setDescription("Mô tả của bài viết")
                .setLink("https://vnexpress.net/gia-xang-dau-co-bien-dong-1234567.html")
                .setPubDate(ZonedDateTime.of(2026, 11, 15, 5, 30, 0, 0, ZoneOffset.ofHours(7)))
                .setSourceDomain("vnexpress.net")
                .setCoverImageSrc("https://vnexpress.net/cover.jpg")
                .setSourceUrl("https://vnexpress.net/rss/kinh-doanh.rss");
    }

    @Test
    void itemIdShouldBeStableForTheSameSourceAndLink() {
        final String itemId = NewsItemDocumentMapper.itemIdOf("vnexpress", sampleNews().getLink());

        assertEquals(itemId, NewsItemDocumentMapper.itemIdOf("vnexpress", sampleNews().getLink()));
        assertTrue(itemId.startsWith("vnexpress_"), "item id should be namespaced by the source key: " + itemId);
        assertTrue(itemId.matches("^[a-z0-9.\\-]+_[0-9a-f]{24}$"), "unexpected item id format: " + itemId);
    }

    @Test
    void itemIdShouldDifferForDifferentLinksOrSources() {
        final String vnexpressId = NewsItemDocumentMapper.itemIdOf("vnexpress", sampleNews().getLink());
        final String otherLinkId = NewsItemDocumentMapper.itemIdOf("vnexpress", "https://vnexpress.net/another-article.html");
        final String otherSourceId = NewsItemDocumentMapper.itemIdOf("tuoitre", sampleNews().getLink());

        assertNotEquals(vnexpressId, otherLinkId);
        assertNotEquals(vnexpressId, otherSourceId);
    }

    @Test
    void itemIdShouldStayDeterministicWhenTheLinkIsMissing() {
        final String first = NewsItemDocumentMapper.itemIdOf("medium", null);
        final String second = NewsItemDocumentMapper.itemIdOf("medium", "   ");

        assertEquals(first, second);
        assertTrue(first.startsWith("medium_"));
    }

    @Test
    void documentShouldCarryEveryQueryableField() {
        final Map<String, Object> document = NewsItemDocumentMapper.toDocument(
                "vnexpress",
                sampleNews(),
                "vi-VN",
                CRAWLED_AT,
                DATE_TIME_FORMATTER
        );

        assertEquals("vnexpress", document.get("sourceKey"));
        assertEquals("vnexpress.net", document.get("sourceDomain"));
        assertEquals("Giá xăng dầu có biến động", document.get("title"));
        assertEquals("Mô tả của bài viết", document.get("description"));
        assertEquals("https://vnexpress.net/gia-xang-dau-co-bien-dong-1234567.html", document.get("link"));
        assertEquals("https://vnexpress.net/cover.jpg", document.get("coverImageSrc"));
        assertEquals("https://vnexpress.net/rss/kinh-doanh.rss", document.get("sourceUrl"));
        assertEquals("vi-VN", document.get("language"));
        assertEquals("2026-11-15T05:30:00.000+07:00", document.get("pubDate"));
        assertEquals(Date.from(CRAWLED_AT.toInstant()), document.get("crawledAt"));
    }

    @Test
    void publishedDateShouldUseATimestampForOrdering() {
        final Map<String, Object> document = NewsItemDocumentMapper.toDocument(
                "vnexpress",
                sampleNews(),
                "vi-VN",
                CRAWLED_AT,
                DATE_TIME_FORMATTER
        );

        final Object publishedTimestamp = document.get("pubDateTs");
        assertInstanceOf(Date.class, publishedTimestamp);
        assertEquals(sampleNews().getPubDate().toInstant(), ((Date) publishedTimestamp).toInstant());
    }

    @Test
    void missingPublishedDateShouldNotBreakMapping() {
        final NewsEntity newsWithoutDate = sampleNews().setPubDate(null);

        final Map<String, Object> document = NewsItemDocumentMapper.toDocument(
                "vnexpress",
                newsWithoutDate,
                "vi-VN",
                CRAWLED_AT,
                DATE_TIME_FORMATTER
        );

        assertEquals("", document.get("pubDate"));
        assertFalse(document.containsKey("pubDateTs"), "no timestamp should be written for an item without a date");
    }
}
