package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVisit;

public class ContactVisitListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(ContactVisit contactVisit) throws Exception {
		notificationService.sendNotificationOnMatch(ContactNotificationTextType.contactVisitProfile,
				repository.one(Contact.class,
						contactVisit.getContactId()),
				repository.one(Contact.class, contactVisit.getContactId2()));
	}

	@PostUpdate
	public void postUpdate(ContactVisit contactVisit) throws Exception {
		notificationService.sendNotificationOnMatch(ContactNotificationTextType.contactVisitProfile,
				repository.one(Contact.class,
						contactVisit.getContactId()),
				repository.one(Contact.class, contactVisit.getContactId2()));
	}
}