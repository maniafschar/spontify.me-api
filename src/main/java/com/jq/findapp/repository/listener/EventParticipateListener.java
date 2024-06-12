package com.jq.findapp.repository.listener;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.service.EventService;
import com.jq.findapp.util.Strings;

@Component
public class EventParticipateListener extends AbstractRepositoryListener<EventParticipate> {
	@Override
	public void postPersist(final EventParticipate eventParticipate) throws Exception {
		final Event event = repository.one(Event.class, eventParticipate.getEventId());
		if (event != null && !event.getContactId().equals(eventParticipate.getContactId())) {
			final Contact contactTo = repository.one(Contact.class, event.getContactId());
			final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
			final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
			final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
			if (event.getType() == EventType.Inquiry)
				notificationService.sendNotification(contactFrom, contactTo,
						ContactNotificationTextType.eventParticipateWithoutLocation,
						Strings.encodeParam("p=" + contactFrom.getId()),
						Strings.formatDate(null,
								new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
										.toEpochMilli()),
								contactTo.getTimezone()),
						event.getDescription());
			else if (event.getType() == EventType.Online)
				notificationService.sendNotification(contactFrom, contactTo,
						ContactNotificationTextType.eventParticipateOnline,
						Strings.encodeParam("p=" + contactFrom.getId()),
						Strings.formatDate(null,
								new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
										.toEpochMilli()),
								contactTo.getTimezone()),
						event.getDescription());
			else if (event.getType() == EventType.Poll) {
				final JsonNode json = new ObjectMapper().readTree(event.getDescription());
				notificationService.sendNotification(contactFrom, contactTo,
						ContactNotificationTextType.eventParticipatePoll,
						Strings.encodeParam("e=" + event.getId()),
						json.get("q").asText(), EventService.getAnswers(json, eventParticipate.getState()));
			} else
				notificationService.sendNotification(contactFrom, contactTo,
						ContactNotificationTextType.eventParticipate,
						Strings.encodeParam("p=" + contactFrom.getId()),
						Strings.formatDate(null,
								new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
										.toEpochMilli()),
								contactTo.getTimezone()),
						event.getDescription(),
						repository.one(Location.class, event.getLocationId()).getName());
		}
	}
}
