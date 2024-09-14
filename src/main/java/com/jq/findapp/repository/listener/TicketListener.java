package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Json;

@Component
public class TicketListener extends AbstractRepositoryListener<Ticket> {
	@Autowired
	private NotificationService notificationService;

	@Override
	public void prePersist(final Ticket ticket) {
		if (ticket.getType() == TicketType.REGISTRATION && !ticket.getSubject().contains("@")) {
			final JsonNode contact = Json.toNode(Json.toString(repository.one(Contact.class, ticket.getContactId())));
			((ObjectNode) contact).put("password", "");
			ticket.setNote(
					Attachment.resolve(ticket.getNote()) + "\n" + Json.toPrettyString(contact));
			Attachment.save(ticket);
		}
	}

	@Override
	public void postPersist(final Ticket entity) {
		if (entity.getType() == TicketType.BLOCK) {
			final Client client = repository.one(Client.class,
					repository.one(Contact.class, entity.getContactId()).getClientId());
			notificationService.sendEmail(client, null, repository.one(Contact.class, client.getAdminId()).getEmail(),
					"Block", Attachment.resolve(entity.getNote()), null);
		}
	}
}
