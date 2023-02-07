package com.jq.findapp.repository.listener;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.util.Text;

@Component
public class ContactChatListener extends AbstractRepositoryListener<ContactChat> {
	@Autowired
	private ExternalService externalService;

	@Override
	public void prePersist(final ContactChat contactChat) throws Exception {
		if (contactChat.getContactId2() == null)
			contactChat.setContactId2(adminId); // Feedback
		if (duplicateCheck(contactChat))
			throw new IllegalArgumentException("duplicate chat");
	}

	@Override
	public void postPersist(final ContactChat contactChat) throws Exception {
		if (duplicateCheck(contactChat)) {
			repository.delete(contactChat);
			throw new IllegalArgumentException("duplicate chat");
		}
		notifyContact(contactChat);
		if (contactChat.getContactId().intValue() == 551 && contactChat.getContactId2().equals(adminId)
				&& contactChat.getNote() != null
				&& contactChat.getNote().indexOf(Attachment.SEPARATOR) < 0)
			createGptAnswer(contactChat);
	}

	@Async
	private void createGptAnswer(ContactChat contactChat) throws Exception {
		final ContactChat chat = new ContactChat();
		chat.setNote(externalService.chatGpt(contactChat.getNote()));
		chat.setContactId(adminId);
		chat.setContactId2(contactChat.getContactId());
		repository.save(chat);
		contactChat.setSeen(Boolean.TRUE);
		repository.save(contactChat);
	}

	@Async
	private void notifyContact(ContactChat contactChat) throws Exception {
		if (duplicateCheck(contactChat)) {
			repository.delete(contactChat);
			return;
		}
		final Contact contactFrom = repository.one(Contact.class, contactChat.getContactId());
		final Contact contactTo = repository.one(Contact.class, contactChat.getContactId2());
		String s = null;
		if (contactChat.getNote() == null)
			s = Text.mail_sentImg.getText(contactTo.getLanguage());
		else {
			s = contactChat.getNote();
			if (s.indexOf(Attachment.SEPARATOR) > -1)
				s = new String(Repository.Attachment.getFile(s), StandardCharsets.UTF_8);
			if (s.indexOf(" :openPos(") == 0)
				s = (contactFrom.getGender() == null || contactFrom.getGender() == 2 ? Text.mail_sentPos2
						: Text.mail_sentPos1)
						.getText(contactTo.getLanguage());
			else if (s.indexOf(" :open(") == 0)
				s = (s.lastIndexOf(" :open(") == 0 ? Text.mail_sentEntry : Text.mail_sentEntries)
						.getText(contactTo.getLanguage());
		}
		notificationService.sendNotification(contactFrom, contactTo, ContactNotificationTextType.chatNew,
				"chat=" + contactFrom.getId(), s);
	}

	private boolean duplicateCheck(ContactChat contactChat) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setLimit(1);
		params.setSearch("contactChat.contactId=" + contactChat.getContactId() + " and contactChat.contactId2="
				+ contactChat.getContactId2());
		final Result result = repository.list(params);
		if (result.size() > 0) {
			if (!Strings.isEmpty(contactChat.getNote())
					&& contactChat.getNote().equals(result.get(0).get("contactChat.note")))
				return true;
			if (contactChat.getImage() != null && result.get(0).get("contactChat.image") != null
					&& Arrays.equals(Attachment.getFile(contactChat.getImage()),
							Attachment.getFile((String) result.get(0).get("contactChat.image"))))
				return true;
		}
		return false;
	}
}