package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class EventRating extends BaseEntity {
	private BigInteger eventParticipateId;
	private Short rating;
	private String description;
	private String image;

	public Short getRating() {
		return rating;
	}

	public void setRating(Short rating) {
		this.rating = rating;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigInteger getEventParticipateId() {
		return eventParticipateId;
	}

	public void setEventParticipateId(BigInteger eventParticipateId) {
		this.eventParticipateId = eventParticipateId;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}
}