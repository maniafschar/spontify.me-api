package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text.TextId;

@Component
public class ContactLinkListener extends AbstractRepositoryListener<ContactLink> {
	@Override
	public void prePersist(ContactLink contactLink) {
		if (contactLink.getStatus() == null)
			contactLink.setStatus(Status.Pending);
	}

	@Override
	public void postPersist(final ContactLink contactLink) {
		if (contactLink.getStatus() == Status.Pending)
			notificationService.sendNotification(
					repository.one(Contact.class, contactLink.getContactId()),
					repository.one(Contact.class, contactLink.getContactId2()),
					TextId.notification_contactFriendRequest,
					Strings.encodeParam("p=" + contactLink.getContactId()));
	}

	@Override
	public void postUpdate(final ContactLink contactLink) {
		if (contactLink.getStatus() == Status.Friends) {
			notificationService.sendNotification(
					repository.one(Contact.class, contactLink.getContactId2()),
					repository.one(Contact.class, contactLink.getContactId()),
					TextId.notification_contactFriendApproved,
					Strings.encodeParam("p=" + contactLink.getContactId2()));
		}
	}
}
