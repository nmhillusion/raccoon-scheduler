package tech.nmhillusion.raccoon_scheduler.helper;

import tech.nmhillusion.raccoon_scheduler.entity.news.NewsEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a crawled news item into one Firestore document so that items become
 * individually queryable (filter by source key, order by published date,
 * paginate with cursors) instead of living inside an array field.
 * <p>
 * created date: 2026-11-15
 */
public final class NewsItemDocumentMapper {
    public static final String FIELD_SOURCE_KEY = "sourceKey";
    public static final String FIELD_SOURCE_DOMAIN = "sourceDomain";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_LINK = "link";
    public static final String FIELD_COVER_IMAGE_SRC = "coverImageSrc";
    public static final String FIELD_SOURCE_URL = "sourceUrl";
    public static final String FIELD_PUB_DATE = "pubDate";
    public static final String FIELD_PUB_DATE_TS = "pubDateTs";
    public static final String FIELD_LANGUAGE = "language";
    public static final String FIELD_CRAWLED_AT = "crawledAt";

    private static final int ITEM_ID_HASH_LENGTH = 24;
    private static final String NO_LINK_SEED = "no-link";

    private NewsItemDocumentMapper() {
    }

    /**
     * Deterministic document id, so that the same article upserts instead of
     * being duplicated when the job runs again before the old item expires.
     */
    public static String itemIdOf(String sourceKey, String link) {
        final String normalizedSourceKey = normalizeForId(sourceKey);
        final String normalizedLink = nullToEmpty(link).trim();
        final String seed = normalizedLink.isEmpty() ? NO_LINK_SEED : normalizedLink;

        return normalizedSourceKey + "_" + sha256Hex(seed).substring(0, ITEM_ID_HASH_LENGTH);
    }

    public static Map<String, Object> toDocument(String sourceKey,
                                                 NewsEntity newsEntity,
                                                 String language,
                                                 ZonedDateTime crawledAt,
                                                 DateTimeFormatter dateTimeFormatter) {
        final Map<String, Object> document = new LinkedHashMap<>();

        document.put(FIELD_SOURCE_KEY, nullToEmpty(sourceKey).trim());
        document.put(FIELD_SOURCE_DOMAIN, nullToEmpty(newsEntity.getSourceDomain()));
        document.put(FIELD_TITLE, nullToEmpty(newsEntity.getTitle()));
        document.put(FIELD_DESCRIPTION, nullToEmpty(newsEntity.getDescription()));
        document.put(FIELD_LINK, nullToEmpty(newsEntity.getLink()));
        document.put(FIELD_COVER_IMAGE_SRC, nullToEmpty(newsEntity.getCoverImageSrc()));
        document.put(FIELD_SOURCE_URL, nullToEmpty(newsEntity.getSourceUrl()));

        final ZonedDateTime pubDate = newsEntity.getPubDate();
        if (null != pubDate) {
            document.put(FIELD_PUB_DATE, null == dateTimeFormatter ? pubDate.toString() : dateTimeFormatter.format(pubDate));
            // a real timestamp is required: Firestore cannot order by the formatted string reliably
            document.put(FIELD_PUB_DATE_TS, Date.from(pubDate.toInstant()));
        } else {
            document.put(FIELD_PUB_DATE, "");
        }

        document.put(FIELD_LANGUAGE, nullToEmpty(language));
        document.put(FIELD_CRAWLED_AT, Date.from((null == crawledAt ? ZonedDateTime.now() : crawledAt).toInstant()));

        return document;
    }

    /**
     * Compares the persisted content of an item with the freshly crawled version.
     * {@link #FIELD_CRAWLED_AT} is ignored: a re-crawl of an unchanged article
     * must not produce another write just to refresh the crawl timestamp.
     */
    public static boolean hasSameContent(Map<String, Object> existingDocument,
                                         Map<String, Object> newDocument) {
        if (null == existingDocument || null == newDocument) {
            return false;
        }

        final Map<String, Object> existingContent = new LinkedHashMap<>(existingDocument);
        final Map<String, Object> newContent = new LinkedHashMap<>(newDocument);
        existingContent.remove(FIELD_CRAWLED_AT);
        newContent.remove(FIELD_CRAWLED_AT);

        return existingContent.equals(newContent);
    }

    private static String normalizeForId(String sourceKey) {
        final String normalized = nullToEmpty(sourceKey).trim();
        // document ids may not contain '/', and must not be empty
        final String safe = normalized.replace('/', '-');
        return safe.isEmpty() ? "unknown-source" : safe;
    }

    private static String nullToEmpty(String value) {
        return null == value ? "" : value;
    }

    private static String sha256Hex(String rawValue) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(nullToEmpty(rawValue).getBytes(StandardCharsets.UTF_8));

            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte hashByte : hash) {
                hex.append(String.format("%02x", hashByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this runtime", ex);
        }
    }
}
