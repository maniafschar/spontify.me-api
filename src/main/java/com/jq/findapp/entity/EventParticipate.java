package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.listener.EventParticipateListener;

@Entity
@EntityListeners(EventParticipateListener.class)
public class EventParticipate extends BaseEntity {
	private BigInteger contactId;
	private BigInteger eventId;
	private Date eventDate;
	private String reason;
	private Short state;

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

	public Date getEventDate() {
		return eventDate;
	}

	public void setEventDate(Date eventDate) {
		this.eventDate = eventDate;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Short getState() {
		return state;
	}

	public void setState(Short state) {
		this.state = state;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}