package com.jq.findapp.repository.listener;

import javax.persistence.PrePersist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository.Attachment;

public class TicketListener extends AbstractRepositoryListener {
	@PrePersist
	public void prePersist(Ticket ticket) throws JsonProcessingException {
		if (ticket.getType() == TicketType.REGISTRATION && !ticket.getSubject().contains("@")) {
			ticket.setNote(ticket.getNote() + "\n"
					+ new ObjectMapper().writerWithDefaultPrettyPrinter()
							.writeValueAsString(repository.one(Contact.class, ticket.getContactId())));
			Attachment.save(ticket);
		}
	}
}
