package com.jq.findapp.repository.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.EventRating;
import com.jq.findapp.entity.Location;
import com.jq.findapp.util.Text.TextId;

@Component
public class EventRatingListener extends AbstractRepositoryListener<EventRating> {
	@Async
	@Override
	public void postPersist(final EventRating eventRating) throws Exception {
		final EventParticipate eventParticipate = repository.one(EventParticipate.class,
				eventRating.getEventParticipateId());
		final Event event = repository.one(Event.class, eventParticipate.getEventId());
		repository.executeUpdate(
				"update Contact contact set rating=(select sum(eventRating.rating)/count(*) from EventRating eventRating, EventParticipate eventParticipate, Event event where event.contactId=contact.id and eventParticipate.eventId=event.id and eventRating.eventParticipateId=eventParticipate.id) where contact.id="
						+ event.getContactId());
		repository.executeUpdate(
				"update Event event set rating=(select sum(eventRating.rating)/count(*) from EventRating eventRating, EventParticipate eventParticipate where eventParticipate.eventId=event.id and eventRating.eventParticipateId=eventParticipate.id) where event.id="
						+ event.getId());
		if (event.getLocationId() != null && repository.one(Location.class, event.getLocationId()) != null) {
			repository.executeUpdate(
					"update Location location set rating=(select sum(eventRating.rating)/count(*) from EventRating eventRating, EventParticipate eventParticipate, Event event where event.locationId=location.id and eventParticipate.eventId=event.id and eventRating.eventParticipateId=eventParticipate.id) where location.id="
							+ event.getLocationId());
			notificationService.locationNotifyOnMatch(
					repository.one(Contact.class, event.getContactId()),
					event.getLocationId(), TextId.notification_eventRated,
					repository.one(Location.class, event.getLocationId()).getName());
		}
	}
}