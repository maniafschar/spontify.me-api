package com.jq.findapp.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Text;

@Service
public class ChatService {
	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	@Value("${app.admin.id}")
	protected BigInteger adminId;

	@Async
	public void createGptAnswer(ContactChat contactChat) throws Exception {
		createGptAnswerIntern(contactChat);
	}

	public SchedulerResult answerAi() {
		final SchedulerResult result=new SchedulerResult(getClass().getSimpleName() + "/answerAi");
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("contactChat.contactId<>" + adminId + " and contactChat.textId='" + Text.engagement_ai.name()
				+ "' and contactChat.createdAt>'"
				+ Instant.now().minus(Duration.ofDays(3)) + "'");
		final Result result2 = repository.list(params);
		for (int i = 0; i < result2.size(); i++) {
			params.setSearch(
					"contactChat.contactId=" + adminId + " and contactChat.id>" + result2.get(i).get("contactChat.id"));
			if (repository.list(params).size() == 0) {
				try {
					createGptAnswer(
							repository.one(ContactChat.class, (BigInteger) result2.get(i).get("contactChat.id")));
				} catch (Exception ex) {
					if (result.exception == null)
						result.exception = ex;
					break;
				}
			}
		}
		return result;
	}

	private void createGptAnswerIntern(ContactChat contactChat) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("contactChat.textId='" + Text.engagement_ai.name() + "' and (contactChat.contactId="
				+ contactChat.getContactId() + " or contactChat.contactId2=" + contactChat.getContactId() + ")");
		if (repository.list(params).size() > 40)
			return;
		contactChat.setSeen(Boolean.TRUE);
		repository.save(contactChat);
		final ContactChat chat = new ContactChat();
		chat.setContactId(adminId);
		chat.setContactId2(contactChat.getContactId());
		chat.setTextId(Text.engagement_ai);
		chat.setNote(externalService.chatGpt(contactChat.getNote()));
		if (chat.getNote().contains("\n\n")) {
			final String s = chat.getNote().substring(0, chat.getNote().indexOf("\n\n"));
			contactChat.setNote(contactChat.getNote() + (Character.isLetterOrDigit(s.charAt(0)) ? " " : "") + s);
			chat.setNote(chat.getNote().substring(chat.getNote().indexOf("\n\n")).trim());
			repository.save(contactChat);
		}
		repository.save(chat);
	}

	@Async
	public void notifyContact(ContactChat contactChat) throws Exception {
		final Contact contactFrom = repository.one(Contact.class, contactChat.getContactId());
		final Contact contactTo = repository.one(Contact.class, contactChat.getContactId2());
		String s = null;
		if (contactChat.getNote() == null)
			s = Text.notification_sentImg.getText(contactTo.getLanguage());
		else {
			s = contactChat.getNote();
			if (s.indexOf(Attachment.SEPARATOR) > -1)
				s = new String(Repository.Attachment.getFile(s), StandardCharsets.UTF_8);
			if (s.indexOf(" :openPos(") == 0)
				s = (contactFrom.getGender() == null || contactFrom.getGender() == 2 ? Text.notification_sentPos2
						: Text.notification_sentPos1)
						.getText(contactTo.getLanguage());
			else if (s.indexOf(" :open(") == 0)
				s = (s.lastIndexOf(" :open(") == 0 ? Text.notification_sentEntry : Text.notification_sentEntries)
						.getText(contactTo.getLanguage());
		}
		notificationService.sendNotification(contactFrom, contactTo, ContactNotificationTextType.chatNew,
				"chat=" + contactFrom.getId(), s);
	}

}
