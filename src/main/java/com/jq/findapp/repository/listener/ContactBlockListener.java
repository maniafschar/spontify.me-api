package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.ContactBlock;
import com.jq.findapp.entity.Ticket.TicketType;

public class ContactBlockListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(final ContactBlock contactBlock) throws Exception {
		if (contactBlock.getReason() != null && contactBlock.getReason() > 0) {
			notificationService.createTicket(TicketType.BLOCK,
					contactBlock.getId() + " > " + contactBlock.getContactId2(),
					"id: " + contactBlock.getId() +
							"\ncontactId: " + contactBlock.getContactId() +
							"\ncontactId2: " + contactBlock.getContactId2() +
							"\nreason: " + contactBlock.getReason() +
							"\nnote: " + contactBlock.getNote());
		}
	}
}