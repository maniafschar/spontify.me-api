package com.jq.findapp.entity;

import java.beans.Transient;
import java.math.BigInteger;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;

@Entity
public class ContactGroupLink extends BaseEntity {
	private BigInteger contactGroupId;
	private BigInteger contactId2;

	public BigInteger getContactId2() {
		return contactId2;
	}

	public void setContactId2(BigInteger contactId2) {
		this.contactId2 = contactId2;
	}

	public BigInteger getContactGroupId() {
		return contactGroupId;
	}

	public void setContactGroupId(BigInteger contactGroupId) {
		this.contactGroupId = contactGroupId;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(repository.one(ContactGroup.class, getContactGroupId()).getContactId());
	}
}
