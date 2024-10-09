package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class ClientNews extends BaseEntity {
	private BigInteger clientId;
	private Float latitude;
	private Float longitude;
	private String skills;
	private String description;
	private String image;
	private String publishId;
	private String source;
	private String url;
	private Timestamp publish;
	private Boolean notified = false;

	public Float getLatitude() {
		return this.latitude;
	}

	public void setLongitude(final Float longitude) {
		this.longitude = longitude;
	}

	public Float getLongitude() {
		return this.longitude;
	}

	public void setLatitude(final Float latitude) {
		this.latitude = latitude;
	}

	public void setPublishId(String publishId) {
		this.publishId = publishId;
	}

	public String getPublishId() {
		return publishId;
	}

	public Boolean getNotified() {
		return this.notified;
	}

	public void setNotified(final boolean notified) {
		this.notified = notified;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public BigInteger getClientId() {
		return this.clientId;
	}

	public void setClientId(final BigInteger clientId) {
		this.clientId = clientId;
	}

	public Timestamp getPublish() {
		return this.publish;
	}

	public void setPublish(final Timestamp publish) {
		this.publish = publish;
	}

	public String getImage() {
		return this.image;
	}

	public void setImage(final String image) {
		this.image = image;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getSource() {
		return this.source;
	}

	public void setSource(final String source) {
		this.source = source;
	}

	public String getSkills() {
		return this.skills;
	}

	public void setSkills(final String skills) {
		this.skills = skills;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		final Contact contact = repository.one(Contact.class, user);
		return contact.getClientId().equals(this.getClientId()) && contact.getType() == ContactType.adminContent;
	}
}