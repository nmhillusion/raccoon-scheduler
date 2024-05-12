package tech.nmhillusion.raccoon_scheduler.service_impl.image;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.nmhillusion.n2mix.util.IOStreamUtil;
import tech.nmhillusion.raccoon_scheduler.entity.image.unsplash.UnsplashImageEntity;
import tech.nmhillusion.raccoon_scheduler.service.image.UnsplashImageService;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-05-12
 */
class UnsplashImageServiceImplTest {

    @Test
    void doExecute() {
        final UnsplashImageService service = Assertions.assertDoesNotThrow(UnsplashImageServiceImpl::new);
        final UnsplashImageEntity randomImageFromUnsplashEntity = Assertions.assertDoesNotThrow(service::getRandomImageFromUnsplash);
        Assertions.assertDoesNotThrow(() -> service.postToStorageServer(
                randomImageFromUnsplashEntity
                , IOStreamUtil.convertInputStreamToByteArray(getClass().getClassLoader().getResourceAsStream("test-data/image/hd.jpg")))
        );
    }
}