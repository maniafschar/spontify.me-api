package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVisit;

public class ContactVisitListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(ContactVisit contactVisit) throws Exception {
		sendNotification(contactVisit);
	}

	@PostUpdate
	public void postUpdate(ContactVisit contactVisit) throws Exception {
		sendNotification(contactVisit);
	}

	private void sendNotification(ContactVisit contactVisit) throws Exception {
		notificationService.sendNotificationOnMatch(ContactNotificationTextType.contactVisitProfile,
				repository.one(Contact.class,
						contactVisit.getContactId()),
				repository.one(Contact.class, contactVisit.getContactId2()));
	}
}