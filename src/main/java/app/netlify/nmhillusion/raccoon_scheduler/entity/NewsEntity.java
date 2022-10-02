package app.netlify.nmhillusion.raccoon_scheduler.entity;

import app.netlify.nmhillusion.raccoon_scheduler.type.Stringeable;

/**
 * date: 2022-09-25
 * <p>
 * created-by: nmhillusion
 */

public class NewsEntity extends Stringeable {
    private String title;
    private String description;
    private String link;
    private String pubDate;
    private String source;

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

    public String getPubDate() {
        return pubDate;
    }

    public NewsEntity setPubDate(String pubDate) {
        this.pubDate = pubDate;
        return this;
    }

    public String getSource() {
        return source;
    }

    public NewsEntity setSource(String source) {
        this.source = source;
        return this;
    }
}
