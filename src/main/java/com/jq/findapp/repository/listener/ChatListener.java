package com.jq.findapp.repository.listener;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.persistence.PostPersist;
import javax.persistence.PrePersist;

import org.apache.logging.log4j.util.Strings;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Text;

public class ChatListener extends AbstractRepositoryListener {
	@PrePersist
	public void prePersist(final Chat chat) throws Exception {
		if (chat.getContactId2() == null && chat.getLocationId() == null)
			chat.setContactId2(adminId); // Feedback
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("chat.contactId=" + chat.getContactId() + " and chat.contactId2=" + chat.getContactId2()
				+ " and chat.locationId=" + chat.getLocationId());
		final Result result = repository.list(params);
		if (result.size() > 0) {
			if (!Strings.isEmpty(chat.getNote()) && chat.getNote().equals(result.get(0).get("chat.note")))
				throw new IllegalArgumentException("duplicate chat");
			if (chat.getImage() != null && result.get(0).get("chat.image") != null
					&& Arrays.equals(Attachment.getFile(chat.getImage()),
							Attachment.getFile((String) result.get(0).get("chat.image"))))
				throw new IllegalArgumentException("duplicate chat");
		}
	}

	@PostPersist
	public void postPersist(final Chat chat) throws Exception {
		final Contact contactFrom = repository.one(Contact.class, chat.getContactId());
		if (chat.getLocationId() == null) {
			final Contact contactTo = repository.one(Contact.class, chat.getContactId2());
			String s = null;
			if (chat.getNote() == null)
				s = Text.mail_sentImg.getText(contactTo.getLanguage());
			else {
				s = chat.getNote();
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
		} else
			notificationService.locationNotifyOnMatch(contactFrom, chat.getLocationId(),
					ContactNotificationTextType.chatLocation, chat.getNote());
	}

}