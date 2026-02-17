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
	private String searchMandatory;

	public BigInteger getAdminId() {
		return this.adminId;
	}

	public void setAdminId(final BigInteger adminId) {
		this.adminId = adminId;
	}

	public String getName() {
		return this.name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public String getStorage() {
		return this.storage;
	}

	public void setStorage(final String storage) {
		this.storage = storage;
	}

	public String getFbPageAccessToken() {
		return this.fbPageAccessToken;
	}

	public void setFbPageAccessToken(final String fbPageAccessToken) {
		this.fbPageAccessToken = fbPageAccessToken;
	}

	public String getFbPageId() {
		return this.fbPageId;
	}

	public void setFbPageId(final String fbPageId) {
		this.fbPageId = fbPageId;
	}

	public String getSearchMandatory() {
		return this.searchMandatory;
	}

	public void setSearchMandatory(final String searchMandatory) {
		this.searchMandatory = searchMandatory;
	}
}