package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;

@Entity
public class ContactToken extends BaseEntity {
	private BigInteger contactId;
	private String token;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}
}