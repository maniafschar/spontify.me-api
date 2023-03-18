package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;

@Entity
public class ContactVideoCall extends BaseEntity {
	private BigInteger contactId;
	private Timestamp time;
	@Enumerated(EnumType.STRING)
	private ContactVideoCallType type;
	private String description;

	public enum ContactVideoCallType {
		AUTHENTICATE, OTHER
	}

	public Timestamp getTime() {
		return time;
	}

	public void setTime(Timestamp time) {
		this.time = time;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public ContactVideoCallType getType() {
		return type;
	}

	public void setType(ContactVideoCallType type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}
