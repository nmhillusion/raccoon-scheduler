package tech.nmhillusion.raccoon_scheduler.service_impl;

import org.junit.jupiter.api.Test;
import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlNewsServiceImplTest {
    final String[] filteredWords = "bạn gái|cưới|hôn|con giáp|sắc|nhạy cảm|bạn trai|tổ ấm|yêu|tình|hẹn hò".split("\\|");

    private boolean isValidFilteredNews(NewsEntity newsEntity) {
        return Stream.of(filteredWords).noneMatch(word ->
                String.valueOf(newsEntity.getTitle()).toLowerCase().contains(String.valueOf(word).toLowerCase())
                        || String.valueOf(newsEntity.getDescription()).toLowerCase().contains(String.valueOf(word).toLowerCase())
        );
    }

    @Test
    void testFilterNews() {
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("Đôi chị em có nhan sắc tuyệt vời")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("Bạn gái của anh trai đó")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("2 diễn viên cũ có tình cảm khăn khít")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("4 con giáp cực tốt trong năm nay")));

        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Các nhà đầu tư tham gia đầu thầu")));
        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Giá xăng dầu có biến động")));
        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Intel ra dòng sản phẩm mới")));
    }
}