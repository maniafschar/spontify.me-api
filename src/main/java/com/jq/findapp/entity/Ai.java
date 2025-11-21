package com.jq.findapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Ai extends BaseEntity {
	private String question;
	@Column(columnDefinition = "TEXT")
	private String note;
	@Enumerated(EnumType.STRING)
	private AiType type;

	public enum AiType {
		Location, LocationAttibutes, Event, Text
	}

	public String getQuestion() {
		return this.question;
	}

	public void setQuestion(final String question) {
		this.question = question;
	}

	public String getNote() {
		return this.note;
	}

	public void setNote(final String note) {
		this.note = note;
	}

	public AiType getType() {
		return this.type;
	}

	public void setType(final AiType type) {
		this.type = type;
	}
}
