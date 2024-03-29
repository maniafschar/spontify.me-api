package com.jq.findapp.entity;

import jakarta.persistence.Entity;

@Entity
public class Setting extends BaseEntity {
	public static transient final int MAX_VALUE_LENGTH = 2000;
	private String label;
	private String data;

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}
}
