package com.jq.findapp.entity;

import jakarta.persistence.Entity;

@Entity
public class Setting extends BaseEntity {
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
