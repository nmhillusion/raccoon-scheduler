package app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers;

import app.netlify.nmhillusion.n2mix.type.Stringeable;

/**
 * date: 2022-11-26
 * <p>
 * created-by: nmhillusion
 */

public class PendingUserEntity extends Stringeable {
    private String fullName;
    private String email;

    public String getFullName() {
        return fullName;
    }

    public PendingUserEntity setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public PendingUserEntity setEmail(String email) {
        this.email = email;
        return this;
    }
}
