package com.jq.findapp.repository.listener;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.util.Strings;

public class EventParticipateListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(EventParticipate eventParticipate) throws Exception {
		final Event event = repository.one(Event.class, eventParticipate.getEventId());
		if (event != null && !event.getContactId().equals(eventParticipate.getContactId())) {
			final Contact contactTo = repository.one(Contact.class, event.getContactId());
			final Contact contactFrom = repository.one(Contact.class,
					eventParticipate.getContactId());
			final Instant time = Instant.ofEpochMilli(
					repository.one(Event.class, eventParticipate.getEventId()).getStartDate()
							.getTime());
			time.minus(Duration.ofMinutes(contactTo.getTimezoneOffset()));
			notificationService.sendNotification(contactFrom, contactTo,
					ContactNotificationTextType.eventParticipate,
					Strings.encodeParam("p=" + contactFrom.getId()),
					new SimpleDateFormat("dd.MM.yyyy").format(eventParticipate.getEventDate()) +
							new SimpleDateFormat(" HH:mm").format(new Date(time.toEpochMilli())),
					event.getText(),
					repository.one(Location.class, event.getLocationId()).getName());
		}
	}
}