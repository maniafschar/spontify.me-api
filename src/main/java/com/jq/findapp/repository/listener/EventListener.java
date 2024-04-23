package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.EventService;
import com.jq.findapp.util.Strings;

@Component
public class EventListener extends AbstractRepositoryListener<Event> {
	@Autowired
	private EventService eventService;

	@Override
	public void prePersist(final Event event) throws Exception {
		preUpdate(event);
	}

	@Override
	public void preUpdate(final Event event) throws Exception {
		if (event.getRepetition() == null)
			event.setRepetition("o");
		if ("o".equals(event.getRepetition()))
			event.setEndDate(new Date(event.getStartDate().getTime()));
	}

	@Override
	public void postPersist(final Event event) throws Exception {
		if (!repository.one(Client.class, repository.one(Contact.class, event.getContactId()).getClientId())
				.getAdminId().equals(event.getContactId())) {
			final EventParticipate eventParticipate = new EventParticipate();
			eventParticipate.setState((short) 1);
			eventParticipate.setContactId(event.getContactId());
			eventParticipate.setEventId(event.getId());
			eventParticipate.setEventDate(new Date(event.getStartDate().getTime()));
			repository.save(eventParticipate);
		}
	}

	@Override
	public void postUpdate(final Event event) throws Exception {
		if (event.old("startDate") != null || event.old("price") != null) {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch(
					"eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate>=cast('"
							+ Instant.now().minus((Duration.ofDays(1))) + "' as timestamp)");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) result.get(i).get("eventParticipate.id"));
				if (event.old("startDate") != null) {
					if ("o".equals(event.getRepetition()))
						eventParticipate.setEventDate(new java.sql.Date(event.getStartDate().getTime()));
					else
						eventParticipate
								.setEventDate(java.sql.Date.valueOf(eventService.getRealDate(event).toLocalDate()));
					repository.save(eventParticipate);
				}
				if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
					final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
					if (time.isAfter(Instant.now())) {
						final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
						final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
						final Contact contactFrom = repository.one(Contact.class, event.getContactId());
						notificationService.sendNotification(contactFrom, contactTo,
								ContactNotificationTextType.eventChanged,
								Strings.encodeParam("e=" + event.getId() + "_" + eventParticipate.getEventDate()),
								Strings.formatDate(null,
										new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
												.toEpochMilli()),
										contactTo.getTimezone()),
								event.getDescription(),
								repository.one(Location.class, event.getLocationId()).getName());
					}
				}
			}
		}
	}

	@Override
	public void postRemove(final Event event) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		if (result.size() > 0 && event.getPrice() != null && event.getPrice() > 0)
			throw new RuntimeException("Paid events with participants cannot be deleted");
		for (int i = 0; i < result.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) result.get(i).get("eventParticipate.id"));
			if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
				final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
				if (time.isAfter(Instant.now())) {
					final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
					final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
					final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
					notificationService.sendNotification(contactFrom, contactTo,
							ContactNotificationTextType.eventDelete,
							Strings.encodeParam("p=" + contactFrom.getId()),
							Strings.formatDate(null,
									new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60).toEpochMilli()),
									contactTo.getTimezone()),
							event.getDescription(),
							repository.one(Location.class, event.getLocationId()).getName());
				}
			}
			repository.delete(eventParticipate);
		}
	}
}
