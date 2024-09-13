package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService.NotificationType;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text.TextId;

@Component
public class ContactListener extends AbstractRepositoryListener<Contact> {
	@Autowired
	private AuthenticationService authenticationService;

	@Override
	public void prePersist(final Contact contact) {
		contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
		contact.setAgeDivers("18,99");
		contact.setAgeFemale("18,99");
		contact.setAgeMale("18,99");
		String notification = NotificationType.birthday + "," + NotificationType.chat + ","
				+ NotificationType.engagement + "," + NotificationType.event + "," + NotificationType.friend + ",";
		try {
			final JsonNode props = Json.toNode(repository.one(com.jq.findapp.entity.Client.class, contact.getClientId()).getStorage());
			if (props.has("rss") && props.get("rss").size() == 1)
				notification += NotificationType.news + ",";
		} catch (Exception ex) {
		}
		notification += NotificationType.visitLocation + "," + NotificationType.visitProfile;
		contact.setNotification(notification);
	}

	@Override
	public void preUpdate(final Contact contact) throws Exception {
		if (contact.old("email") != null)
			contact.setAuthenticate(Boolean.FALSE);
		if (contact.old("visitPage") != null)
			contact.setVisitPage(new Timestamp(Instant.now().toEpochMilli()));
		if (!Strings.isEmpty(contact.old("pushToken")))
			repository.executeUpdate(
					"update Contact contact set contact.pushToken=null, contact.pushSystem=null where contact.pushToken='"
							+ contact.old("pushToken") + "' and contact.id<>" + contact.getId());
		if (!Strings.isEmpty(contact.old("fbToken") != null))
			repository.executeUpdate(
					"update Contact contact set contact.fbToken=null where contact.fbToken='"
							+ contact.old("fbToken")
							+ "' and contact.id<>" + contact.getId());
		if (!Strings.isEmpty(contact.old("pseudonym")))
			contact.setPseudonym(sanitizePseudonym(contact.getPseudonym()));
		if (!Strings.isEmpty(contact.old("password")))
			contact.setLoginLink(null);
		if (contact.getBirthday() == null)
			contact.setAge(null);
		else {
			final GregorianCalendar now = new GregorianCalendar();
			final GregorianCalendar birthday = new GregorianCalendar();
			birthday.setTimeInMillis(contact.getBirthday().getTime());
			short age = (short) (now.get(Calendar.YEAR) - birthday.get(Calendar.YEAR));
			if (now.get(Calendar.MONTH) < birthday.get(Calendar.MONTH) ||
					now.get(Calendar.MONTH) == birthday.get(Calendar.MONTH) &&
							now.get(Calendar.DAY_OF_MONTH) < birthday.get(Calendar.DAY_OF_MONTH))
				age--;
			contact.setAge(age);
		}
	}

	@Async
	@Override
	public void postUpdate(final Contact contact) throws Exception {
		if (contact.old("email") != null)
			authenticationService.recoverSendEmail(contact.getEmail(), contact.getClientId());
	}

	@Override
	public void preRemove(final Contact contact) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listFriends);
		params.setUser(contact);
		params.setLimit(0);
		params.setSearch("(contactLink.contactId=" + contact.getId() + " or contactLink.contactId2=" + contact.getId()
				+ ") and contactLink.status='Friends'");
		repository.list(params).forEach(e -> {
			final ContactLink contactLink = repository.one(ContactLink.class,
					(BigInteger) e.get("contactLink.id"));
			notificationService.sendNotification(contact,
					repository.one(Contact.class,
							contactLink.getContactId().equals(contact.getId()) ? contactLink.getContactId2()
									: contactLink.getContactId()),
					TextId.notification_contactDelete, null);
		});
	}

	private String sanitizePseudonym(String pseudonym) {
		pseudonym = pseudonym.trim().replaceAll("[^a-zA-ZÀ-ÿ0-9-_.+*#§$%&/\\\\ \\^']", "");
		int i = 0;
		while (pseudonym.length() < 4)
			pseudonym += (char) ('a' + i++);
		return pseudonym;
	}
}
