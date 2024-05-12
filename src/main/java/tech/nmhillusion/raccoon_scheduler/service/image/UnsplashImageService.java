package tech.nmhillusion.raccoon_scheduler.service.image;

import tech.nmhillusion.raccoon_scheduler.entity.image.unsplash.UnsplashImageEntity;
import tech.nmhillusion.raccoon_scheduler.service.BaseSchedulerService;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-04-16
 */
public interface UnsplashImageService extends BaseSchedulerService {
    UnsplashImageEntity getRandomImageFromUnsplash() throws Exception;

    String postToStorageServer(UnsplashImageEntity imageEntity, byte[] imageData) throws Throwable;
}
