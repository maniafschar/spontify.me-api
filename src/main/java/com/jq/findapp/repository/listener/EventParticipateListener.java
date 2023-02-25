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
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.util.Strings;

@Component
public class EventParticipateListener extends AbstractRepositoryListener<EventParticipate> {
	@Override
	public void postPersist(final EventParticipate eventParticipate) throws Exception {
		if (eventParticipate.getModifiedAt() == null) {
			final Event event = repository.one(Event.class, eventParticipate.getEventId());
			if (event != null && !event.getContactId().equals(eventParticipate.getContactId())) {
				final Contact contactTo = repository.one(Contact.class, event.getContactId());
				final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
				final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
				final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
				if (event.getLocationId() == null || event.getLocationId().intValue() == -2)
					notificationService.sendNotification(contactFrom, contactTo,
							ContactNotificationTextType.eventParticipateWithoutLocation,
							Strings.encodeParam("p=" + contactFrom.getId()),
							Strings.formatDate(null,
									new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
											.toEpochMilli()),
									contactTo.getTimezone()),
							event.getText());
				else if (event.getLocationId().intValue() == -1)
					notificationService.sendNotification(contactFrom, contactTo,
							ContactNotificationTextType.eventParticipateOnline,
							Strings.encodeParam("p=" + contactFrom.getId()),
							Strings.formatDate(null,
									new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
											.toEpochMilli()),
									contactTo.getTimezone()),
							event.getText());
				else
					notificationService.sendNotification(contactFrom, contactTo,
							ContactNotificationTextType.eventParticipate,
							Strings.encodeParam("p=" + contactFrom.getId()),
							Strings.formatDate(null,
									new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
											.toEpochMilli()),
									contactTo.getTimezone()),
							event.getText(),
							repository.one(Location.class, event.getLocationId()).getName());
			}
		}
	}
}