package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ClientCategory extends BaseEntity {
	private BigInteger clientId;
	private Integer category;
	private String description;
	private String image;

	public BigInteger getClientId() {
		return this.clientId;
	}

	public void setClientId(final BigInteger clientId) {
		this.clientId = clientId;
	}

	public Integer getCategory() {
		return this.category;
	}

	public void setCategory(final Integer category) {
		this.category = category;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public String getImage() {
		return this.image;
	}

	public void setImage(final String image) {
		this.image = image;
	}
}