package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.util.Strings;

@Component
public class ContactLinkListener extends AbstractRepositoryListener<ContactLink> {
	@Override
	public void postPersist(final ContactLink contactLink) throws Exception {
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
			final QueryParams params = new QueryParams(Query.contact_marketing);
			params.setUser(repository.one(Contact.class, contactLink.getContactId()));
			params.setSearch(
					"contactMarketing.data='" + contactLink.getContactId2() + "' and contactMarketing.type='"
							+ ContactMarketing.ContactMarketingType.CollectFriends.name()
							+ "' and contactMarketing.contactId="
							+ contactLink.getContactId());
			if (repository.list(params).size() == 0) {
				final ContactMarketing marketing = new ContactMarketing();
				marketing.setContactId(contactLink.getContactId());
				marketing.setData(contactLink.getContactId2().toString());
				marketing.setType(ContactMarketing.ContactMarketingType.CollectFriends);
				repository.save(marketing);
			}
		}
	}
}