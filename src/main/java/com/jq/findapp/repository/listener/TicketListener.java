package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository.Attachment;

@Component
public class TicketListener extends AbstractRepositoryListener<Ticket> {
	@Override
	public void prePersist(final Ticket ticket) throws JsonProcessingException {
		if (ticket.getType() == TicketType.REGISTRATION && !ticket.getSubject().contains("@")) {
			final JsonNode contact = new ObjectMapper().convertValue(
					repository.one(Contact.class, ticket.getContactId()), JsonNode.class);
			((ObjectNode) contact).put("password", "");
			ticket.setNote(ticket.getNote() + "\n" + new ObjectMapper().writerWithDefaultPrettyPrinter()
					.writeValueAsString(contact));
			Attachment.save(ticket);
		}
	}
}
