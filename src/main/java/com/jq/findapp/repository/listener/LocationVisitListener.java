package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.LocationVisit;

public class LocationVisitListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(LocationVisit locationVisit) throws Exception {
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationVisit.getContactId()),
				locationVisit.getLocationId(), ContactNotificationTextType.contactVisitLocation, null);
	}
}