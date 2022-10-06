package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

import javax.persistence.PostRemove;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;

public class EventListener extends AbstractRepositoryListener {
	@PostRemove
	public void postPersist(Event event) throws Exception {
		final QueryParams params = new QueryParams(Query.event_participate);
		params.setSearch("eventParicipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) result.get(i).get("eventParticipate.id"));
			if (eventParticipate.getState() == 1) {
				final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
				final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
				final Instant time = Instant.ofEpochMilli(
						repository.one(Event.class, eventParticipate.getEventId()).getStartDate()
								.getTime());
				time.plusSeconds(contactTo.getTimezoneOffset() * 60);
				notificationService.sendNotification(contactFrom, contactTo,
						NotificationID.eventParticipate,
						Strings.encodeParam("p=" + contactFrom.getId()),
						new SimpleDateFormat("dd.MM.yyyy").format(eventParticipate.getEventDate()) +
								new SimpleDateFormat(" HH:mm").format(new Date(time.toEpochMilli())),
						event.getText(),
						repository.one(Location.class, event.getLocationId()).getName());
			}
		}
	}
}