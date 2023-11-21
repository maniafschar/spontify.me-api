package com.jq.findapp.entity;

import jakarta.persistence.Entity;

@Entity
public class Storage extends BaseEntity {
	private String storage;
	private String label;

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}