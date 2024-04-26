package tech.nmhillusion.raccoon_scheduler.entity.news;

import tech.nmhillusion.n2mix.type.Stringeable;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

public class NewsEntity extends Stringeable {
    private String title;
    private String description;
    private String link;
    private ZonedDateTime pubDate;
    private String sourceDomain;
    private String coverImageSrc;
    private String sourceUrl;

    public String getTitle() {
        return title;
    }

    public NewsEntity setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public NewsEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getLink() {
        return link;
    }

    public NewsEntity setLink(String link) {
        this.link = link;
        return this;
    }

    public ZonedDateTime getPubDate() {
        return pubDate;
    }

    public NewsEntity setPubDate(ZonedDateTime pubDate) {
        this.pubDate = pubDate;
        return this;
    }

    public String getSourceDomain() {
        return sourceDomain;
    }

    public NewsEntity setSourceDomain(String sourceDomain) {
        this.sourceDomain = sourceDomain;
        return this;
    }

    public String getCoverImageSrc() {
        return coverImageSrc;
    }

    public NewsEntity setCoverImageSrc(String coverImageSrc) {
        this.coverImageSrc = coverImageSrc;
        return this;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public NewsEntity setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewsEntity that)) return false;
        return Objects.equals(getLink(), that.getLink());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLink());
    }
}
