package app.netlify.nmhillusion.raccoon_scheduler.entity.image.unsplash;

import tech.nmhillusion.n2mix.type.Stringeable;

/**
 * created by: nmhillusion
 * <p>
 * created date: 2024-04-16
 */
public class UnsplashImageEntity extends Stringeable {
    private String id;
    private String downloadFullUrl;
    private String description;
    private int width;
    private int height;
    private String color;
    private String mimeType;
    private String htmlLink;

    public String getId() {
        return id;
    }

    public UnsplashImageEntity setId(String id) {
        this.id = id;
        return this;
    }

    public String getDownloadFullUrl() {
        return downloadFullUrl;
    }

    public UnsplashImageEntity setDownloadFullUrl(String downloadFullUrl) {
        this.downloadFullUrl = downloadFullUrl;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public UnsplashImageEntity setDescription(String description) {
        this.description = description;
        return this;
    }

    public int getWidth() {
        return width;
    }

    public UnsplashImageEntity setWidth(int width) {
        this.width = width;
        return this;
    }

    public int getHeight() {
        return height;
    }

    public UnsplashImageEntity setHeight(int height) {
        this.height = height;
        return this;
    }

    public String getColor() {
        return color;
    }

    public UnsplashImageEntity setColor(String color) {
        this.color = color;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public UnsplashImageEntity setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public String getHtmlLink() {
        return htmlLink;
    }

    public UnsplashImageEntity setHtmlLink(String htmlLink) {
        this.htmlLink = htmlLink;
        return this;
    }
}
