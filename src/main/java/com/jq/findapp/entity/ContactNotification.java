package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Transient;

import com.jq.findapp.repository.Repository;

@Entity
public class ContactNotification extends BaseEntity {
	private String text;
	private String action;
	private ContactNotificationTextType textType;
	private BigInteger contactId;
	private BigInteger contactId2;
	private BigInteger sentStatus;
	private Boolean seen = false;
	@Enumerated(EnumType.STRING)
	private ContactNotificationType type;

	public enum ContactNotificationType {
		android, ios, email
	}

	public enum ContactNotificationTextType {
		chatLocation,
		chatNew,
		chatSeen,
		contactBirthday,
		contactDelete,
		contactFindMe,
		contactFriendApproved,
		contactFriendRequest,
		contactVisitLocation,
		contactVisitProfile,
		contactWhatToDo,
		eventDelete,
		eventNotify,
		eventNotification,
		eventParticipate,
		locationMarketing,
		locationRatingMatch;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public ContactNotificationTextType getTextType() {
		return textType;
	}

	public void setTextType(ContactNotificationTextType textType) {
		this.textType = textType;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getContactId2() {
		return contactId2;
	}

	public void setContactId2(BigInteger contactId2) {
		this.contactId2 = contactId2;
	}

	public BigInteger getSentStatus() {
		return sentStatus;
	}

	public void setSentStatus(BigInteger sentStatus) {
		this.sentStatus = sentStatus;
	}

	public Boolean getSeen() {
		return seen;
	}

	public void setSeen(Boolean seen) {
		this.seen = seen;
	}

	public ContactNotificationType getType() {
		return type;
	}

	public void setType(ContactNotificationType type) {
		this.type = type;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return user.equals(getContactId());
	}
}