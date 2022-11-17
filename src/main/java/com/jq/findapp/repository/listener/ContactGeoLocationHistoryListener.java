package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.ImportLocationsService;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Autowired
	private EventService eventService;

	@Autowired
	private ImportLocationsService importLocationsService;

	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {
		eventService.notifyCheckInOut(contactGeoLocationHistory.getContactId());
		final GeoLocation geoLocation = repository.one(GeoLocation.class, contactGeoLocationHistory.getGeoLocationId());
		importLocationsService.lookup(geoLocation.getLatitude(), geoLocation.getLongitude());
	}
}