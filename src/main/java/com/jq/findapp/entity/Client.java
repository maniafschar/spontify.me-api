package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class Client extends BaseEntity {
	private BigInteger adminId;
	private String storage;
	private String name;
	private String email;
	private String url;

	public BigInteger getAdminId() {
		return adminId;
	}

	public void setAdminId(final BigInteger adminId) {
		this.adminId = adminId;
	}

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}
}