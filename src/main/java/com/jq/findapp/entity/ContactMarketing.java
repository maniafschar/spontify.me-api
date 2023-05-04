package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ContactMarketing extends BaseEntity {
	private BigInteger contactId;
	private BigInteger clientMarketingId;
	private String storage;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getClientMarketingId() {
		return clientMarketingId;
	}

	public void setClientMarketingId(BigInteger clientMarketingId) {
		this.clientMarketingId = clientMarketingId;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}
}