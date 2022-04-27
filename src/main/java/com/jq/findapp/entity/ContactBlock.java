package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;

@Entity
public class ContactBlock extends BaseEntity {
	private BigInteger contactId;
	private BigInteger contactId2;
	private String note;
	private Short reason;

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

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Short getReason() {
		return reason;
	}

	public void setReason(Short reason) {
		this.reason = reason;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}
