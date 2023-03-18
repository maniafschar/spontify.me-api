package com.jq.findapp.entity;

import java.beans.Transient;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

import javax.persistence.Entity;

import com.jq.findapp.repository.Repository;

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
	// TODO rm
	private String text;
	private String type;
	private String url;
	private Timestamp startDate;

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(BigInteger locationId) {
		this.locationId = locationId;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Timestamp getStartDate() {
		return startDate;
	}

	public void setStartDate(Timestamp startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getImageList() {
		return imageList;
	}

	public void setImageList(String imageList) {
		this.imageList = imageList;
	}

	public void setConfirm(Short confirm) {
		this.confirm = confirm;
	}

	public void setPrice(Double price) {
		this.price = price;
	}

	public void setMaxParticipants(Short maxParticipants) {
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

	public void setSkills(String skills) {
		this.skills = skills;
	}

	public String getSkillsText() {
		return skillsText;
	}

	public void setSkillsText(String skillsText) {
		this.skillsText = skillsText;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}