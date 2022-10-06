package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;
import javax.persistence.PrePersist;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.service.NotificationService.NotificationID;

public class ContactBluetoothListener extends AbstractRepositoryListener {
	@PrePersist
	public void prePersist(final ContactBluetooth contactBlutooth) {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		contactBlutooth.setLatitude(me.getLatitude());
		contactBlutooth.setLongitude(me.getLongitude());
	}

	@PostPersist
	public void postPersist(final ContactBluetooth contactBlutooth)
			throws Exception {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		final Contact other = repository.one(Contact.class, contactBlutooth.getContactId2());
		if (me.getFindMe() != null && me.getFindMe() && other.getFindMe() != null && other.getFindMe()) {
			notificationService.sendNotificationOnMatch(NotificationID.contactFindMe, me, other);
			notificationService.sendNotificationOnMatch(NotificationID.contactFindMe, other, me);
		}
	}
}