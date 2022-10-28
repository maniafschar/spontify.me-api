package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationRating;

@Component
public class LocationRatingListener extends AbstractRepositoryListener<LocationRating> {
	@Override
	public void postPersist(final LocationRating locationRating) throws Exception {
		repository.executeUpdate(
				"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id) where location.id="
						+ locationRating.getLocationId());
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationRating.getContactId()),
				locationRating.getLocationId(), ContactNotificationTextType.locationRatingMatch,
				repository.one(Location.class, locationRating.getLocationId()).getName());
	}
}