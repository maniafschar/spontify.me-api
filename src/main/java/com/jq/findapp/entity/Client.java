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
	private String fbPageAccessToken;
	private String fbPageId;

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

	public String getFbPageAccessToken() {
		return fbPageAccessToken;
	}

	public void setFbPageAccessToken(final String fbPageAccessToken) {
		this.fbPageAccessToken = fbPageAccessToken;
	}

	public String getFbPageId() {
		return fbPageId;
	}

	public void setFbPageId(final String fbPageId) {
		this.fbPageId = fbPageId;
	}
}