package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Timestamp;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.listener.ContactWhatToDoListener;

@Entity
@EntityListeners(ContactWhatToDoListener.class)
public class ContactWhatToDo extends BaseEntity {
	private Boolean active = true;
	private BigInteger contactId;
	private BigInteger eventId;
	private BigInteger locationId;
	private Timestamp time;
	private String keywords;
	private String budget;
	private String message;

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getEventId() {
		return eventId;
	}

	public void setEventId(BigInteger eventId) {
		this.eventId = eventId;
	}

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(BigInteger locationId) {
		this.locationId = locationId;
	}

	public String getKeywords() {
		return keywords;
	}

	public void setKeywords(String keywords) {
		this.keywords = keywords;
	}

	public String getBudget() {
		return budget;
	}

	public void setBudget(String budget) {
		this.budget = budget;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Timestamp getTime() {
		return time;
	}

	public void setTime(Timestamp time) {
		this.time = time;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}