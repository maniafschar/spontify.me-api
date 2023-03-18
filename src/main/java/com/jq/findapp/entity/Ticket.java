package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Ticket extends BaseEntity {
	private BigInteger contactId;
	private String subject;
	private String note;
	@Enumerated(EnumType.STRING)
	private TicketType type;

	public enum TicketType {
		ERROR, REGISTRATION, BLOCK, GOOGLE, EMAIL, LOCATION, PAYPAL
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public TicketType getType() {
		return type;
	}

	public void setType(TicketType type) {
		this.type = type;
	}
}