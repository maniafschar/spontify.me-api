package com.jq.findapp.api.model;

public class Marketing {
	public final String title;
	public final String text;
	public final String action;

	public Marketing(String title, String text, String action) {
		this.title = title;
		this.text = text;
		this.action = action;
	}

	public String getTitle() {
		return title;
	}

	public String getText() {
		return text;
	}

	public String getAction() {
		return action;
	}
}