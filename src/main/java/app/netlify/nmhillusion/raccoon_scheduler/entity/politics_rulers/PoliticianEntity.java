package app.netlify.nmhillusion.raccoon_scheduler.entity.politics_rulers;

import app.netlify.nmhillusion.n2mix.type.Stringeable;

import java.time.LocalDate;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

public class PoliticianEntity extends Stringeable {
    private String fullName;
    private LocalDate dateOfBirth;
    private LocalDate dateOfDeath;
    private String role;
    private String note;

    public String getFullName() {
        return fullName;
    }

    public PoliticianEntity setFullName(String fullName) {
        this.fullName = fullName;
        return this;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public PoliticianEntity setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
        return this;
    }

    public LocalDate getDateOfDeath() {
        return dateOfDeath;
    }

    public PoliticianEntity setDateOfDeath(LocalDate dateOfDeath) {
        this.dateOfDeath = dateOfDeath;
        return this;
    }

    public String getRole() {
        return role;
    }

    public PoliticianEntity setRole(String role) {
        this.role = role;
        return this;
    }

    public String getNote() {
        return note;
    }

    public PoliticianEntity setNote(String note) {
        this.note = note;
        return this;
    }
}
