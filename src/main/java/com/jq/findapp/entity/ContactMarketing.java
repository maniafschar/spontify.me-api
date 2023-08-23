package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ContactMarketing extends BaseEntity {
	private BigInteger contactId;
	private BigInteger clientMarketingId;
	private String storage;
	private Boolean finished = false;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getClientMarketingId() {
		return clientMarketingId;
	}

	public void setClientMarketingId(final BigInteger clientMarketingId) {
		this.clientMarketingId = clientMarketingId;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}

	public Boolean getFinished() {
		return finished;
	}

	public void setFinished(final Boolean finished) {
		this.finished = finished;
	}
}