package com.jq.findapp.entity;

import java.math.BigInteger;

import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;

@Entity
public class ContactNotification extends BaseEntity {
	private String text;
	private String action;
	private BigInteger contactId;
	private BigInteger contactId2;
	private BigInteger sentStatus;
	private Boolean seen = false;
	@Enumerated(EnumType.STRING)
	private ContactNotificationType type;
	@Enumerated(EnumType.STRING)
	private ContactNotificationTextType textType;

	public enum ContactNotificationType {
		android, ios, email
	}

	public enum ContactNotificationTextType {
		chatNew,
		chatSeen,
		clientNews,
		contactBirthday,
		contactDelete,
		contactFindMe,
		contactFriendApproved,
		contactFriendRequest,
		contactVideoCall,
		contactVisitLocation,
		contactVisitProfile,
		eventChanged,
		eventDelete,
		eventNotify,
		eventNotifyWithoutLocation,
		eventNotification,
		eventParticipate,
		eventParticipateOnline,
		eventParticipateWithoutLocation,
		eventRated;
	}

	public String getText() {
		return text;
	}

	public void setText(final String text) {
		this.text = text;
	}

	public String getAction() {
		return action;
	}

	public void setAction(final String action) {
		this.action = action;
	}

	public ContactNotificationTextType getTextType() {
		return textType;
	}

	public void setTextType(final ContactNotificationTextType textType) {
		this.textType = textType;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getContactId2() {
		return contactId2;
	}

	public void setContactId2(final BigInteger contactId2) {
		this.contactId2 = contactId2;
	}

	public BigInteger getSentStatus() {
		return sentStatus;
	}

	public void setSentStatus(final BigInteger sentStatus) {
		this.sentStatus = sentStatus;
	}

	public Boolean getSeen() {
		return seen;
	}

	public void setSeen(final Boolean seen) {
		this.seen = seen;
	}

	public ContactNotificationType getType() {
		return type;
	}

	public void setType(final ContactNotificationType type) {
		this.type = type;
	}

	@Transient
	@Override
	public boolean writeAccess(final BigInteger user, final Repository repository) {
		return user.equals(getContactId());
	}
}