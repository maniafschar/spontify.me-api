package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.util.Strings;

@Component
public class ContactLinkListener extends AbstractRepositoryListener<ContactLink> {
	@Override
	public void prePersist(ContactLink contactLink) throws Exception {
		final Contact contact = repository.one(Contact.class, contactLink.getContactId2());
		if (contact.getAcceptFriendship() != null && contact.getAcceptFriendship())
			contactLink.setStatus(Status.Friends);
		else
			contactLink.setStatus(Status.Pending);
	}

	@Override
	public void postPersist(final ContactLink contactLink) throws Exception {
		if (contactLink.getStatus() == Status.Pending)
			notificationService.sendNotification(
					repository.one(Contact.class, contactLink.getContactId()),
					repository.one(Contact.class, contactLink.getContactId2()),
					ContactNotificationTextType.contactFriendRequest,
					Strings.encodeParam("p=" + contactLink.getContactId()));
	}

	@Override
	public void postUpdate(final ContactLink contactLink) throws Exception {
		if (contactLink.getStatus() == Status.Friends) {
			notificationService.sendNotification(
					repository.one(Contact.class, contactLink.getContactId2()),
					repository.one(Contact.class, contactLink.getContactId()),
					ContactNotificationTextType.contactFriendApproved,
					Strings.encodeParam("p=" + contactLink.getContactId2()));
		}
	}
}
