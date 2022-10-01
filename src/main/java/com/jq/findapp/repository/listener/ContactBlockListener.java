package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.ContactBlock;
import com.jq.findapp.entity.Ticket.Type;

public class ContactBlockListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(final ContactBlock contactBlock) throws Exception {
		notificationService.createTicket(Type.BLOCK, contactBlock.getId() + " > " + contactBlock.getContactId2(),
				"id: " + contactBlock.getId() +
						"\ncontactId: " + contactBlock.getContactId() +
						"\ncontactId2: " + contactBlock.getContactId2() +
						"\nreason: " + contactBlock.getReason() +
						"\nnote: " + contactBlock.getNote());
	}
}