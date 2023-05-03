package com.jq.findapp.entity;

import java.math.BigInteger;

import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class Client extends BaseEntity {
	private String css;
	private String name;
	private String email;
	private String url;
	private String storage;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getCss() {
		return css;
	}

	public void setCss(String css) {
		this.css = css;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return repository.one(Contact.class, user).getType() == ContactType.adminContent;
	}
}