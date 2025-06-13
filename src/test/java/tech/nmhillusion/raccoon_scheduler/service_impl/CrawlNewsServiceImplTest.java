package tech.nmhillusion.raccoon_scheduler.service_impl;

import org.junit.jupiter.api.Test;
import tech.nmhillusion.n2mix.util.StringUtil;
import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlNewsServiceImplTest {
    final List<Pattern> filteredWords = Stream.of("bạn gái|cưới|hôn|con giáp|sắc|nhạy cảm|bạn trai|tổ ấm|yêu|tình|hẹn hò|\\d+ tuổi".split("\\|"))
            .map(it -> Pattern.compile("(^|\\W)" + it + "(\\W|$)", Pattern.CASE_INSENSITIVE))
            .toList();

    private boolean isValidFilteredNews(NewsEntity newsEntity) {
        return filteredWords.stream()
                .noneMatch(wordPattern ->
                        wordPattern
                                .matcher(StringUtil.trimWithNull(newsEntity.getTitle()))
                                .find()
                );
    }

    @Test
    void testFilterNews() {
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("Đôi chị em có nhan sắc tuyệt vời")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("Bạn gái của anh trai đó")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("2 diễn viên cũ có tình cảm khăn khít")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("4 con giáp cực tốt trong năm nay")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("3 tuổi này được quan tâm nhiều nhất")));
        assertFalse(isValidFilteredNews(new NewsEntity().setTitle("Chú tâm vào 2 tuổi này")));

        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Các nhà đầu tư tham gia đầu thầu")));
        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Giá xăng dầu có biến động")));
        assertTrue(isValidFilteredNews(new NewsEntity().setTitle("Intel ra dòng sản phẩm mới")));
    }
}