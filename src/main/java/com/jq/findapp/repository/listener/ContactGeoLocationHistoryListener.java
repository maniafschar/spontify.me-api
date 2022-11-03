package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.EventService;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Autowired
	private EventService eventService;

	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {
		final QueryParams params = new QueryParams(Query.location_eventParticipate);
		params.setSearch("eventParticipate.contactId=" + contactGeoLocationHistory.getContactId()
				+ " and event.contactId=eventParticipate.contactId and event.marketingEvent=true and event.startDate>'"
				+ Instant.now().minus(Duration.ofHours(1)) + "' and event.startDate<'"
				+ Instant.now().plus(Duration.ofHours(2)) + "' and eventParticipate.state=1");
		final Result events = repository.list(params);
		if (events.size() > 0) {
			final GeoLocation geoLocation = repository.one(GeoLocation.class,
					contactGeoLocationHistory.getGeoLocationId());
			eventService.sendCheckInOut((BigInteger) events.get(0).get("event.id"),
					geoLocation.getLatitude(), geoLocation.getLongitude(), true);
		}
	}
}