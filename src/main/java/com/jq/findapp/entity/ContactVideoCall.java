package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;

@Entity
public class ContactVideoCall extends BaseEntity {
	private BigInteger contactId;
	private Timestamp time;
	@Enumerated(EnumType.STRING)
	private ContactVideoCallType type;

	public enum ContactVideoCallType {
		AUTHENTICATE
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

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}
