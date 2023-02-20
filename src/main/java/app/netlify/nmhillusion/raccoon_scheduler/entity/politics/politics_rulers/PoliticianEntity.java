package app.netlify.nmhillusion.raccoon_scheduler.entity.politics.politics_rulers;

import app.netlify.nmhillusion.n2mix.type.Stringeable;

import java.time.LocalDate;

/**
 * date: 2022-11-17
 * <p>
 * created-by: nmhillusion
 */

public class PoliticianEntity extends Stringeable {
	private String originalParagraph;

	private String fullName;
	private String primaryName;
	private String secondaryName;
	private LocalDate dateOfBirth;
	private String placeOfBirth;
	private LocalDate dateOfDeath;
	private String placeOfDeath;
	private String position;
	private String note;


	public String getOriginalParagraph() {
		return originalParagraph;
	}

	public PoliticianEntity setOriginalParagraph(String originalParagraph) {
		this.originalParagraph = originalParagraph;
		return this;
	}

	public String getFullName() {
		return fullName;
	}

	public PoliticianEntity setFullName(String fullName) {
		this.fullName = fullName;
		return this;
	}

	public String getPrimaryName() {
		return primaryName;
	}

	public PoliticianEntity setPrimaryName(String primaryName) {
		this.primaryName = primaryName;
		return this;
	}

	public String getSecondaryName() {
		return secondaryName;
	}

	public PoliticianEntity setSecondaryName(String secondaryName) {
		this.secondaryName = secondaryName;
		return this;
	}

	public LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public PoliticianEntity setDateOfBirth(LocalDate dateOfBirth) {
		this.dateOfBirth = dateOfBirth;
		return this;
	}

	public String getPlaceOfBirth() {
		return placeOfBirth;
	}

	public PoliticianEntity setPlaceOfBirth(String placeOfBirth) {
		this.placeOfBirth = placeOfBirth;
		return this;
	}

	public LocalDate getDateOfDeath() {
		return dateOfDeath;
	}

	public PoliticianEntity setDateOfDeath(LocalDate dateOfDeath) {
		this.dateOfDeath = dateOfDeath;
		return this;
	}

	public String getPlaceOfDeath() {
		return placeOfDeath;
	}

	public PoliticianEntity setPlaceOfDeath(String placeOfDeath) {
		this.placeOfDeath = placeOfDeath;
		return this;
	}

	public String getPosition() {
		return position;
	}

	public PoliticianEntity setPosition(String position) {
		this.position = position;
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
