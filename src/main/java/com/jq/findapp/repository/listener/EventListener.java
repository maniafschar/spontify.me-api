package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
	public void postPersist(Event event) throws Exception {
		final EventParticipate eventParticipate = new EventParticipate();
		eventParticipate.setState((short) 1);
		eventParticipate.setContactId(event.getContactId());
		eventParticipate.setEventId(event.getId());
		eventParticipate.setEventDate(new java.sql.Date(event.getStartDate().getTime()));
		repository.save(eventParticipate);
	}

	@Override
	public void postUpdate(final Event event) throws Exception {
		if (event.old("startDate") != null || event.old("price") != null) {
			final QueryParams params = new QueryParams(Query.location_eventParticipate);
			params.setSearch("eventParticipate.eventId=" + event.getId());
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) result.get(i).get("eventParticipate.id"));
				final LocalTime eventDate = event.getStartDate().toInstant().atZone(ZoneOffset.UTC).toLocalTime();
				if (event.old("startDate") != null) {
					if ("o".equals(event.getType()))
						eventParticipate.setEventDate(new java.sql.Date(event.getStartDate().getTime()));
					else
						eventParticipate.setEventDate(java.sql.Date.valueOf(eventService.getRealDate(event,
								LocalDate.ofInstant(
										Instant.ofEpochMilli(eventParticipate.getEventDate().getTime()),
										ZoneId.systemDefault()))));
					repository.save(eventParticipate);
				}
				if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
					final Instant time = eventParticipate.getEventDate().toInstant()
							.plus(Duration.ofSeconds((eventDate.getHour() * 60 + eventDate.getMinute()) * 60));
					if (time.isAfter(Instant.now())) {
						final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
						final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
						time.minus(Duration.ofMinutes(contactTo.getTimezoneOffset()));
						notificationService.sendNotification(contactFrom, contactTo,
								ContactNotificationTextType.eventChanged,
								Strings.encodeParam("e=" + event.getId()),
								new SimpleDateFormat("dd.MM.yyyy").format(eventParticipate.getEventDate()) +
										new SimpleDateFormat(" HH:mm").format(new Date(time.toEpochMilli())),
								event.getText(),
								repository.one(Location.class, event.getLocationId()).getName());
					}
				}
			}
		}
	}

	@Override
	public void postRemove(final Event event) throws Exception {
		final QueryParams params = new QueryParams(Query.location_eventParticipate);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) result.get(i).get("eventParticipate.id"));
			if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
				final LocalTime eventDate = event.getStartDate().toInstant().atZone(ZoneOffset.UTC).toLocalTime();
				final Instant time = eventParticipate.getEventDate().toInstant()
						.plus(Duration.ofSeconds((eventDate.getHour() * 60 + eventDate.getMinute()) * 60));
				if (time.isAfter(Instant.now())) {
					final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
					final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
					time.minus(Duration.ofMinutes(contactTo.getTimezoneOffset()));
					notificationService.sendNotification(contactFrom, contactTo,
							ContactNotificationTextType.eventDelete,
							Strings.encodeParam("p=" + contactFrom.getId()),
							new SimpleDateFormat("dd.MM.yyyy").format(eventParticipate.getEventDate()) +
									new SimpleDateFormat(" HH:mm").format(new Date(time.toEpochMilli())),
							event.getText(),
							repository.one(Location.class, event.getLocationId()).getName());
				}
			}
			repository.delete(eventParticipate);
		}
	}
}