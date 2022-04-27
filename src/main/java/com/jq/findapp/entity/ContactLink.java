package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.RepositoryListener;

@Entity
@EntityListeners(RepositoryListener.class)
public class ContactLink extends BaseEntity {
	private BigInteger contactId;
	private BigInteger contactId2;
	@Enumerated(EnumType.STRING)
	private Status status;

	public enum Status {
		Pending,
		Friends,
		Rejected,
		Terminated,
		Terminated2
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getContactId2() {
		return contactId2;
	}

	public void setContactId2(BigInteger contactId2) {
		this.contactId2 = contactId2;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId()) || user.equals(getContactId2());
	}
}