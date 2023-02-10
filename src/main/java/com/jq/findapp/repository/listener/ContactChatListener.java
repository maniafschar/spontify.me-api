package com.jq.findapp.repository.listener;

import java.util.Arrays;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ChatService;
import com.jq.findapp.util.Text;

@Component
public class ContactChatListener extends AbstractRepositoryListener<ContactChat> {
	@Autowired
	private ChatService chatService;

	@Override
	public void prePersist(final ContactChat contactChat) throws Exception {
		if (duplicateCheck(contactChat))
			throw new IllegalArgumentException("duplicate chat");
	}

	@Override
	public void postPersist(final ContactChat contactChat) throws Exception {
		if (duplicateCheck(contactChat)) {
			repository.delete(contactChat);
			throw new IllegalArgumentException("duplicate chat");
		}
		chatService.notifyContact(contactChat);
		if (contactChat.getTextId() == Text.engagement_ai
				&& contactChat.getContactId2().equals(adminId)
				&& contactChat.getNote() != null
				&& contactChat.getNote().indexOf(Attachment.SEPARATOR) < 0)
			chatService.createGptAnswer(contactChat);
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