package com.jq.findapp.entity;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
public class Ticket extends BaseEntity {
	private String subject;
	private String note;
	@Enumerated(EnumType.STRING)
	private Type type;

	public enum Type {
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

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}
}