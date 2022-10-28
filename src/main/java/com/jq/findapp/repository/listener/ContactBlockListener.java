package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactBlock;
import com.jq.findapp.entity.Ticket.TicketType;

@Component
public class ContactBlockListener extends AbstractRepositoryListener<ContactBlock> {
	@Override
	public void postPersist(final ContactBlock contactBlock) throws Exception {
		if (contactBlock.getReason() != null && contactBlock.getReason() > 0) {
			notificationService.createTicket(TicketType.BLOCK,
					contactBlock.getId() + " > " + contactBlock.getContactId2(),
					"id: " + contactBlock.getId() +
							"\ncontactId: " + contactBlock.getContactId() +
							"\ncontactId2: " + contactBlock.getContactId2() +
							"\nreason: " + contactBlock.getReason() +
							"\nnote: " + contactBlock.getNote(),
					contactBlock.getContactId());
		}
	}
}