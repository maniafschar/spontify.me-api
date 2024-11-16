package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.service.ImportLocationsService;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Autowired
	private ImportLocationsService importLocationsService;

	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) {
		final GeoLocation geoLocation = repository.one(GeoLocation.class, contactGeoLocationHistory.getGeoLocationId());
		importLocationsService.lookup(geoLocation.getLatitude(), geoLocation.getLongitude());
	}
}