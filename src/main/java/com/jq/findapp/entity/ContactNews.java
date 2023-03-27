package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import jakarta.persistence.Entity;

@Entity
public class ContactNews extends BaseEntity {
	private BigInteger contactId;
	private String description;
	private String image;
	private String url;
	private Timestamp publish;

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public Timestamp getPublish() {
		return publish;
	}

	public void setPublish(Timestamp publish) {
		this.publish = publish;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}