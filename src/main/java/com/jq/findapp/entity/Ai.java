package com.jq.findapp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Ai extends BaseEntity {
	private String question;
	private String answer;
	@Enumerated(EnumType.STRING)
	private AiType type;

	public enum AiType {
		Location, Event, Text
	}

	public String getQuestion() {
		return this.question;
	}

	public void setQuestion(final String question) {
		this.question = question;
	}

	public String getAnswer() {
		return this.answer;
	}

	public void setAnswer(final String answer) {
		this.answer = answer;
	}

	public AiType getType() {
		return this.type;
	}

	public void setType(final AiType type) {
		this.type = type;
	}
}