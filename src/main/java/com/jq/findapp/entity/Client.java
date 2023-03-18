package com.jq.findapp.entity;

import javax.persistence.Entity;

@Entity
public class Client extends BaseEntity {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}
