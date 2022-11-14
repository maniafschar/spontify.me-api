package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Block;
import com.jq.findapp.entity.Ticket.TicketType;

@Component
public class BlockListener extends AbstractRepositoryListener<Block> {
	@Override
	public void postPersist(final Block block) throws Exception {
		if (block.getReason() != null && block.getReason() > 0) {
			notificationService.createTicket(TicketType.BLOCK,
					block.getContactId() + " > " +
							(block.getContactId2() != null ? "contact " + block.getContactId2()
									: block.getLocationId() != null ? "location " + block.getLocationId()
											: "event " + block.getEventId()),
					"id: " + block.getId() +
							"\ncontactId: " + block.getContactId() +
							"\ncontactId2: " + block.getContactId2() +
							"\nlocationId: " + block.getLocationId() +
							"\neventtId: " + block.getEventId() +
							"\nreason: " + block.getReason() +
							"\nnote: " + block.getNote(),
					block.getContactId());
		}
	}
}