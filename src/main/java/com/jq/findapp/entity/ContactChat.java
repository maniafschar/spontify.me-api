package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

import com.jq.findapp.repository.listener.ContactChatListener;
import com.jq.findapp.util.Text;

@Entity
@EntityListeners(ContactChatListener.class)
public class ContactChat extends BaseEntity {
	private BigInteger contactId;
	private BigInteger contactId2;
	private Boolean seen = false;
	private String note;
	private String action;
	private String image;
	@Enumerated(EnumType.STRING)
	private Text textId;

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

	public Text getTextId() {
		return textId;
	}

	public void setTextId(Text textId) {
		this.textId = textId;
	}
}