package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;

@Service
public class EventService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	public void findAndNotify() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch(
				"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
						"length(contact.attrInterest)>0 or length(contact.attrInterestEx)>0 or " +
						"length(contact.attr0)>0 or length(contact.attr0Ex)>0 or " +
						"length(contact.attr1)>0 or length(contact.attr1Ex)>0 or " +
						"length(contact.attr2)>0 or length(contact.attr2Ex)>0 or " +
						"length(contact.attr3)>0 or length(contact.attr3Ex)>0 or " +
						"length(contact.attr4)>0 or length(contact.attr4Ex)>0 or " +
						"length(contact.attr5)>0 or length(contact.attr5Ex)>0)");
		params.setLimit(0);
		final Result ids = repository.list(params);
		params.setQuery(Query.event_listCurrent);
		params.setDistance(20);
		params.setSearch("TO_DAYS(event.startDate)-1<=TO_DAYS(current_timestamp)");
		final LocalDate today = LocalDate.now(ZoneId.systemDefault());
		for (int i = 0; i < ids.size(); i++) {
			params.setUser(repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")));
			final Result events = repository.list(params);
			for (int i2 = 0; i2 < events.size(); i2++) {
				final Event event = repository.one(Event.class, (BigInteger) events.get(i2).get("event.id"));
				final LocalDate realDate = getRealDate(event, today);
				if (realDate.minusDays(1).isBefore(today) && !isMaxParticipants(event, realDate) &&
						notificationService.sendNotification(repository.one(Contact.class, event.getContactId()),
								params.getUser(), NotificationID.event, Strings.encodeParam("e=" + event.getId()),
								(String) events.get(i2).get("location.name")))
					break;
			}
		}
	}

	private LocalDate getRealDate(Event event, LocalDate today) {
		LocalDate realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDate();
		if (!"o".equals(event.getType())) {
			while (realDate.isBefore(today)) {
				if ("w".equals(event.getType()))
					realDate = realDate.plusWeeks(1);
				else if ("w2".equals(event.getType()))
					realDate = realDate.plusWeeks(2);
				else if ("m".equals(event.getType()))
					realDate = realDate.plusMonths(1);
				else
					realDate = realDate.plusYears(1);
			}
		}
		return realDate;
	}

	private boolean isMaxParticipants(Event event, LocalDate date) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.event_participate);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate='"
				+ date + "' and eventParticipate.state=1");
		return repository.list(params).size() >= event.getMaxParticipants();
	}
}
