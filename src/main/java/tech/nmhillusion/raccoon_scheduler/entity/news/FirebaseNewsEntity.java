package tech.nmhillusion.raccoon_scheduler.entity.news;

import tech.nmhillusion.n2mix.type.Stringeable;

import java.time.format.DateTimeFormatter;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-01-21
 */
public class FirebaseNewsEntity extends Stringeable {
    private String title;
    private String description;
    private String link;
    private String pubDate;
    private String sourceDomain;
    private String coverImageSrc;
    private String sourceUrl;

    private FirebaseNewsEntity() {
    }

    public static FirebaseNewsEntity fromNewsEntity(NewsEntity newsEntity, DateTimeFormatter dateTimeFormatter) {
        return new FirebaseNewsEntity()
                .setCoverImageSrc(newsEntity.getCoverImageSrc())
                .setDescription(newsEntity.getDescription())
                .setLink(newsEntity.getLink())
                .setPubDate(
                        null != newsEntity.getPubDate()
                                ? dateTimeFormatter.format(newsEntity.getPubDate())
                                : ""
                )
                .setSourceDomain(newsEntity.getSourceDomain())
                .setSourceUrl(newsEntity.getSourceUrl())
                .setTitle(newsEntity.getTitle())
                ;
    }

    public String getTitle() {
        return title;
    }

    public FirebaseNewsEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirebaseNewsEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getLink() {
        return link;
    }

    public FirebaseNewsEntity setLink(String link) {
        this.link = link;
        return this;
    }

    public String getPubDate() {
        return pubDate;
    }

    public FirebaseNewsEntity setPubDate(String pubDate) {
        this.pubDate = pubDate;
        return this;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public FirebaseNewsEntity setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
        return this;
    }

    public String getCoverImageSrc() {
        return coverImageSrc;
    }

    public FirebaseNewsEntity setCoverImageSrc(String coverImageSrc) {
        this.coverImageSrc = coverImageSrc;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public FirebaseNewsEntity setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }
}
