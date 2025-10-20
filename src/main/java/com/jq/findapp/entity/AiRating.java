package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class AiRating extends BaseEntity {
	private BigInteger aiId;
	private BigInteger contactId;
	private Short rating;

	public BigInteger getAiId() {
		return this.aiId;
	}

	public void setAiId(final BigInteger aiId) {
		this.aiId = aiId;
	}

	public BigInteger getContactId() {
		return this.contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public Short getValue() {
		return this.rating;
	}

	public void setValue(final Short rating) {
		this.rating = rating;
	}
}