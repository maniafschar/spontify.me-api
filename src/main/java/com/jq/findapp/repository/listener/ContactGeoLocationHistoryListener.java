package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.service.EventService;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Autowired
	private EventService eventService;

	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {
		eventService.notifyCheckInOut(contactGeoLocationHistory.getContactId());
	}
}