package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;

@Component
public class TicketListener extends AbstractRepositoryListener<Ticket> {
	@Autowired
	private NotificationService notificationService;

	@Override
	public void prePersist(final Ticket ticket) throws JsonProcessingException {
		if (ticket.getType() == TicketType.REGISTRATION && !ticket.getSubject().contains("@")) {
			final JsonNode contact = new ObjectMapper().convertValue(
					repository.one(Contact.class, ticket.getContactId()), JsonNode.class);
			((ObjectNode) contact).put("password", "");
			ticket.setNote(
					Attachment.resolve(ticket.getNote()) + "\n" + new ObjectMapper().writerWithDefaultPrettyPrinter()
							.writeValueAsString(contact));
			Attachment.save(ticket);
		}
	}

	@Override
	public void postPersist(final Ticket entity) throws Exception {
		if (entity.getType() == TicketType.BLOCK) {
			final Client client = repository.one(Client.class, repository.one(Contact.class, entity.getContactId());
			notificationService.sendEmail(client, "", repository.one(Contact.class, client.getClientId()).getAdminId()),
					"Block", Attachment.resolve(entity.getNote()), null);
		}
	}
}
