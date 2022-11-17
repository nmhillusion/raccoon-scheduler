package app.netlify.nmhillusion.raccoon_scheduler.entity;

import app.netlify.nmhillusion.raccoon_scheduler.type.Stringeable;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

public class PoliticsRulersIndexEntity extends Stringeable {
    private String href;
    private String title;

    public String getHref() {
        return href;
    }

    public PoliticsRulersIndexEntity setHref(String href) {
        this.href = href;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public PoliticsRulersIndexEntity setTitle(String title) {
        this.title = title;
        return this;
    }
}
