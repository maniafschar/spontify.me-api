package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventRating;
import com.jq.findapp.entity.Location;

@Component
public class EventRatingListener extends AbstractRepositoryListener<EventRating> {
	@Override
	public void postPersist(final EventRating eventRating) throws Exception {
		final Event event = repository.one(Event.class, eventRating.getEventId());
		repository.executeUpdate(
				"update Contact contact set rating=(select sum(rating)/count(*) from EventRating eventRating, Event event where event.contactId=contact.id and eventRating.eventId=event.id) where contact.id="
						+ event.getContactId());
		repository.executeUpdate(
				"update Location location set rating=(select sum(rating)/count(*) from EventRating eventRating, Event event where event.locationId=location.id and eventRating.eventId=event.id) where location.id="
						+ event.getLocationId());
		repository.executeUpdate(
				"update Event event set rating=(select sum(rating)/count(*) from EventRating eventRating where eventRating.eventId=event.id) where event.id="
						+ event.getId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, event.getContactId()),
				event.getLocationId(), ContactNotificationTextType.eventRated,
				repository.one(Location.class, event.getLocationId()).getName());
	}
}