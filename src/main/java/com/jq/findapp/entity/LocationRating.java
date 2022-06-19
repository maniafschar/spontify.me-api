package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;

import com.jq.findapp.repository.RepositoryListener;

@Entity
@EntityListeners(RepositoryListener.class)
public class LocationRating extends BaseEntity {
	private BigInteger contactId;
	private BigInteger locationId;
	private Short rating;
	private Short paid;
	private String text;
	private String image;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(BigInteger locationId) {
		this.locationId = locationId;
	}

	public Short getPaid() {
		return paid;
	}

	public void setPaid(Short paid) {
		this.paid = paid;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}
}