package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class ChatService {
	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	@Autowired
	private Text text;

	@Async
	public void createGptAnswer(final ContactChat contactChat) {
		createGptAnswerIntern(contactChat);
	}

	public boolean isVideoCallAllowed(final Contact contact, final BigInteger id) {
		if (repository.one(Client.class, contact.getClientId()).getAdminId().equals(contact.getId()))
			return true;
		final QueryParams params = new QueryParams(Query.contact_listFriends);
		params.setUser(contact);
		params.setId(id);
		final Result list = repository.list(params);
		return list.size() > 0 &&
				(ContactLink.Status.Friends == list.get(0).get("contactLink.status") ||
						ContactLink.Status.Pending == list.get(0).get("contactLink.status") &&
								!list.get(0).get("contactLink.contactId").equals(contact.getId()));
	}

	public CronResult run() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch(
				"contactChat.contactId<>(select adminId from Client client, Contact contact where client.id=contact.clientId and contact.id=contactChat.contactId) and cast(contactChat.textId as text)='"
						+ TextId.engagement_ai.name()
						+ "' and contactChat.createdAt>cast('"
						+ Instant.now().minus(Duration.ofDays(3)) + "' as timestamp)");
		int count = 0;
		final Result result2 = repository.list(params);
		for (int i = 0; i < result2.size(); i++) {
			params.setSearch(
					"contactChat.contactId=(select adminId from Client client, Contact contact where client.id=contact.clientId and contact.id=contactChat.contactId) and contactChat.id>"
							+ result2.get(i).get("contactChat.id"));
			if (repository.list(params).size() == 0) {
				try {
					createGptAnswer(
							repository.one(ContactChat.class, (BigInteger) result2.get(i).get("contactChat.id")));
					count++;
				} catch (final Exception ex) {
					if (result.exception == null)
						result.exception = ex;
					break;
				}
			}
		}
		result.body = "" + count;
		return result;
	}

	private void createGptAnswerIntern(final ContactChat contactChat) {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("cast(contactChat.textId as text)='" + TextId.engagement_ai.name()
				+ "' and (contactChat.contactId="
				+ contactChat.getContactId() + " or contactChat.contactId2=" + contactChat.getContactId() + ")");
		if (repository.list(params).size() > 40)
			return;
		contactChat.setSeen(Boolean.TRUE);
		repository.save(contactChat);
		final ContactChat chat = new ContactChat();
		chat.setContactId(
				repository.one(Client.class, repository.one(Contact.class, contactChat.getContactId()).getClientId())
						.getAdminId());
		chat.setContactId2(contactChat.getContactId());
		chat.setTextId(TextId.engagement_ai);
		chat.setNote(externalService.chatGpt(contactChat.getNote()));
		if (chat.getNote().contains("\n\n")) {
			final String s = chat.getNote().substring(0, chat.getNote().indexOf("\n\n"));
			contactChat.setNote(contactChat.getNote() + (Character.isLetterOrDigit(s.charAt(0)) ? " " : "") + s);
			chat.setNote(chat.getNote().substring(chat.getNote().indexOf("\n\n")).trim());
			repository.save(contactChat);
		}
		repository.save(chat);
	}

	public void notifyContact(final ContactChat contactChat) {
		final Contact contactFrom = repository.one(Contact.class, contactChat.getContactId());
		final Contact contactTo = repository.one(Contact.class, contactChat.getContactId2());
		String s = null;
		if (contactChat.getNote() == null)
			s = text.getText(contactTo, TextId.notification_sentImg);
		else {
			s = Attachment.resolve(contactChat.getNote());
			if (s.indexOf(" :openPos(") == 0)
				s = text.getText(contactTo,
						contactFrom.getGender() == null || contactFrom.getGender() == 2 ? TextId.notification_sentPos2
								: TextId.notification_sentPos1);
			else if (s.indexOf(" :open(") == 0)
				s = text.getText(contactTo, s.lastIndexOf(" :open(") == 0 ? TextId.notification_sentEntry
						: TextId.notification_sentEntries);
		}
		notificationService.sendNotification(contactFrom, contactTo, TextId.notification_chatNew,
				"chat=" + contactFrom.getId(), s);
	}

}
