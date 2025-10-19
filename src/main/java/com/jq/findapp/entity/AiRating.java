package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class AiRating extends BaseEntity {
	private BigInteger aiId;
	private BigInteger contactId;
	private Boolean value;

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

	public Boolean getValue() {
		return this.value;
	}

	public void setValue(final Boolean value) {
		this.value = value;
	}
}