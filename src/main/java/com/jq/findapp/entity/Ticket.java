package com.jq.findapp.entity;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
public class Ticket extends BaseEntity {
	private String subject;
	private String note;
	@Enumerated(EnumType.STRING)
	private TicketType type;

	public enum TicketType {
		ERROR, REGISTRATION, BLOCK, GOOGLE
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