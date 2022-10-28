package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository.Attachment;

@Component
public class TicketListener extends AbstractRepositoryListener<Ticket> {
	@Override
	public void prePersist(final Ticket ticket) throws JsonProcessingException {
		if (ticket.getType() == TicketType.REGISTRATION && !ticket.getSubject().contains("@")) {
			ticket.setNote(ticket.getNote() + "\n"
					+ new ObjectMapper().writerWithDefaultPrettyPrinter()
							.writeValueAsString(repository.one(Contact.class, ticket.getContactId())));
			Attachment.save(ticket);
		}
	}
}
