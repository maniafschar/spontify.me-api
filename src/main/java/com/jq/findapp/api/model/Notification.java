package com.jq.findapp.api.model;

import java.math.BigInteger;
import java.util.List;

public class Notification {
	private List<BigInteger> ids;
	private String text;

	public List<BigInteger> getIds() {
		return ids;
	}

	public void setIds(List<BigInteger> ids) {
		this.ids = ids;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}