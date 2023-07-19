package com.jq.findapp.entity;

import java.beans.Transient;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;

@Entity
public class Event extends BaseEntity {
	private BigInteger contactId;
	private BigInteger locationId;
	private Date endDate;
	private Double price;
	private Short confirm;
	private Short maxParticipants;
	private Short rating;
	private String description;
	private String image;
	private String imageList;
	private String skills;
	private String skillsText;
	private String repetition;
	private String url;
	private Timestamp startDate;

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(final BigInteger locationId) {
		this.locationId = locationId;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public Timestamp getStartDate() {
		return startDate;
	}

	public void setStartDate(final Timestamp startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(final Date endDate) {
		this.endDate = endDate;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(final String imageList) {
		this.imageList = imageList;
	}

	public void setConfirm(final Short confirm) {
		this.confirm = confirm;
	}

	public void setPrice(final Double price) {
		this.price = price;
	}

	public void setMaxParticipants(final Short maxParticipants) {
		this.maxParticipants = maxParticipants;
	}

	public Short getConfirm() {
		return confirm;
	}

	public Double getPrice() {
		return price;
	}

	public Short getMaxParticipants() {
		return maxParticipants;
	}

	public String getSkills() {
		return skills;
	}

	public void setSkills(final String skills) {
		this.skills = skills;
	}

	public String getSkillsText() {
		return skillsText;
	}

	public void setSkillsText(final String skillsText) {
		this.skillsText = skillsText;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(final Short rating) {
		this.rating = rating;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getRepetition() {
		return repetition;
	}

	public void setRepetition(final String repetition) {
		this.repetition = repetition;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return user.equals(getContactId());
	}
}