package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.util.Text.TextId;

@Component
public class ContactBluetoothListener extends AbstractRepositoryListener<ContactBluetooth> {
	@Override
	public void prePersist(final ContactBluetooth contactBlutooth) {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		contactBlutooth.setLatitude(me.getLatitude());
		contactBlutooth.setLongitude(me.getLongitude());
	}

	@Override
	public void postPersist(final ContactBluetooth contactBlutooth) throws Exception {
		final Contact me = repository.one(Contact.class, contactBlutooth.getContactId());
		final Contact other = repository.one(Contact.class, contactBlutooth.getContactId2());
		if (me.getBluetooth() != null && me.getBluetooth() && other.getBluetooth() != null && other.getBluetooth()) {
			notificationService.sendNotificationOnMatch(TextId.notification_contactFindMe, me, other);
			notificationService.sendNotificationOnMatch(TextId.notification_contactFindMe, other, me);
		}
	}
}