package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.util.Text.TextId;

@Component
public class LocationVisitListener extends AbstractRepositoryListener<LocationVisit> {
	@Override
	public void postPersist(final LocationVisit locationVisit) {
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationVisit.getContactId()),
				locationVisit.getLocationId(), TextId.notification_contactVisitLocation, null);
	}
}