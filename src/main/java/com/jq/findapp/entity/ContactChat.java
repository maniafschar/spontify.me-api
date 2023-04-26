package com.jq.findapp.entity;

import java.math.BigInteger;

import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class ContactChat extends BaseEntity {
	private BigInteger contactId;
	private BigInteger contactId2;
	private Boolean seen = false;
	private String note;
	private String action;
	private String image;
	@Enumerated(EnumType.STRING)
	private TextId textId;

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

	public Boolean getSeen() {
		return seen;
	}

	public void setSeen(Boolean seen) {
		this.seen = seen;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public TextId getTextId() {
		return textId;
	}

	public void setTextId(TextId textId) {
		this.textId = textId;
	}
}