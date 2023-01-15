package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.EventRating;
import com.jq.findapp.entity.Location;

@Component
public class EventRatingListener extends AbstractRepositoryListener<EventRating> {
	@Override
	public void postPersist(final EventRating eventRating) throws Exception {
		repository.executeUpdate(
				"update Contact contact set rating=(select sum(rating)/count(*) from EventRating where contactId=contact.id) where contact.id="
						+ eventRating.getContactId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, eventRating.getContactId()),
				eventRating.getLocationId(), ContactNotificationTextType.eventRated,
				repository.one(Location.class, eventRating.getLocationId()).getName());
	}
}