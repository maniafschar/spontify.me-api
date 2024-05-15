package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ContactBonus extends BaseEntity {
	private BigInteger contactId;
	private String description;
	private int bonus;

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public BigInteger getContactId() {
		return this.contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public int getBonus() {
		return bonus;
	}

	public void setBonus(int bonus) {
		this.bonus = bonus;
	}
}