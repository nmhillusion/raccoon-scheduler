package app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers;

import app.netlify.nmhillusion.raccoon_scheduler.type.Stringeable;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

public class IndexEntity extends Stringeable {
    private String href;
    private String title;

    public String getHref() {
        return href;
    }

    public IndexEntity setHref(String href) {
        this.href = href;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public IndexEntity setTitle(String title) {
        this.title = title;
        return this;
    }
}
