package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;

@Entity
public class ContactNotification extends BaseEntity {
	private String text;
	private String action;
	private String textId;
	private BigInteger contactId;
	private BigInteger contactId2;
	private BigInteger sentStatus;
	private Boolean seen = false;

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getTextId() {
		return textId;
	}

	public void setTextId(String textId) {
		this.textId = textId;
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

	public BigInteger getSentStatus() {
		return sentStatus;
	}

	public void setSentStatus(BigInteger sentStatus) {
		this.sentStatus = sentStatus;
	}

	public Boolean getSeen() {
		return seen;
	}

	public void setSeen(Boolean seen) {
		this.seen = seen;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getId());
	}
}