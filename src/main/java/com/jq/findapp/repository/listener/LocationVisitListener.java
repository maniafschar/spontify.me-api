package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.service.NotificationService.NotificationID;

public class LocationVisitListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(LocationVisit locationVisit) throws Exception {
		notificationService.locationNotifyOnMatch(
				repository.one(Contact.class, locationVisit.getContactId()),
				locationVisit.getLocationId(), NotificationID.contactVisitLocation, null);
	}
}