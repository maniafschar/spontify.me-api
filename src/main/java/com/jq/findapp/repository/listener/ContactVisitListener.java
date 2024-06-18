package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.util.Text.TextId;

@Component
public class ContactVisitListener extends AbstractRepositoryListener<ContactVisit> {
	@Override
	public void postPersist(final ContactVisit contactVisit) throws Exception {
		sendNotification(contactVisit);
	}

	@Override
	public void postUpdate(final ContactVisit contactVisit) throws Exception {
		sendNotification(contactVisit);
	}

	private void sendNotification(final BaseEntity entity) throws Exception {
		final ContactVisit contactVisit = (ContactVisit) entity;
		notificationService.sendNotificationOnMatch(TextId.notification_contactVisitProfile,
				repository.one(Contact.class,
						contactVisit.getContactId()),
				repository.one(Contact.class, contactVisit.getContactId2()));
	}
}