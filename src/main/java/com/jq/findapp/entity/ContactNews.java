package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class ContactNews extends BaseEntity {
	private BigInteger contactId;
	private String description;
	private String image;
	private String url;
	private Timestamp publish;
	private Boolean notified = false;

	public Boolean getNotified() {
		return notified;
	}

	public void setNotified(final boolean notified) {
		this.notified = notified;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public Timestamp getPublish() {
		return publish;
	}

	public void setPublish(final Timestamp publish) {
		this.publish = publish;
	}

	public String getImage() {
		return image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return user.equals(getContactId());
	}
}