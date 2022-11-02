package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.util.Text;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {
		final QueryParams params = new QueryParams(Query.location_eventParticipate);
		params.setSearch("eventParticipate.contactId=" + contactGeoLocationHistory.getContactId()
				+ " and event.contactId=eventParticipate.contactId and event.marketingEvent=true and event.startDate>'"
				+ Instant.now().minus(Duration.ofHours(1)) + "' and event.startDate<'"
				+ Instant.now().plus(Duration.ofHours(2)) + "'");
		final Result participations = repository.list(params);
		if (participations.size() == 0)
			return;
		final Event event = repository.one(Event.class, (BigInteger) participations.get(0).get("event.startDate"));
		final Location location = repository.one(Location.class, event.getLocationId());
		final GeoLocation geoLocation = repository.one(GeoLocation.class,
				contactGeoLocationHistory.getGeoLocationId());
		final double distance = GeoLocationProcessor.distance(location.getLatitude(), location.getLongitude(),
				geoLocation.getLatitude(), geoLocation.getLongitude());
		final double maxDistance = 100;
		params.setQuery(Query.contact_chat);
		params.setSearch("chat.textId='" + Text.marketing_eventCheckedIn + "' and chat.createdAt>"
				+ Instant.now().minus(Duration.ofDays(1)));
		final Instant eventTime = Instant.ofEpochMilli(event.getStartDate().getTime());
		if (repository.list(params).size() == 0) {
			if (Instant.now().plus(Duration.ofMinutes(10)).isAfter(eventTime)) {
				if (distance < maxDistance)
					sendChat(event, Text.marketing_eventCheckedIn, distance);
				else if (Instant.now().minus(Duration.ofMinutes(10)).isAfter(eventTime)) {
					params.setSearch("chat.textId='" + Text.marketing_eventCheckedInFailed + "' and chat.createdAt>"
							+ Instant.now().minus(Duration.ofHours(6)));
					if (repository.list(params).size() == 0)
						sendChat(event, Text.marketing_eventCheckedInFailed, distance);
				}
			}
		} else {
			if (Instant.now().minus(Duration.ofHours(1)).isAfter(eventTime)) {
				params.setSearch("chat.textId='" + Text.marketing_eventCheckedOut + "' and chat.createdAt>"
						+ Instant.now().minus(Duration.ofHours(6)));
				if (repository.list(params).size() == 0) {
					if (distance < maxDistance)
						sendChat(event, Text.marketing_eventCheckedOut, distance);
					else {
						params.setSearch(
								"chat.textId='" + Text.marketing_eventCheckedOutFailed + "' and chat.createdAt>"
										+ Instant.now().minus(Duration.ofHours(6)));
						if (repository.list(params).size() == 0)
							sendChat(event, Text.marketing_eventCheckedOutFailed, distance);
					}
				}
			}
		}
	}

	private void sendChat(Event event, Text text, double distance) throws Exception {
		final String d;
		if (distance > 1000)
			d = String.format("%.1f", distance) + "km";
		else
			d = ((int) distance) + "m";
		final Contact contact = repository.one(Contact.class, event.getContactId());
		final Chat chat = new Chat();
		chat.setContactId(adminId);
		chat.setContactId2(contact.getId());
		chat.setSeen(false);
		chat.setTextId(text);
		chat.setNote(text.getText(contact.getLanguage()).replace("<jq:EXTRA_1 />", contact.getPseudonym())
				.replace("<jq:EXTRA_2 />", repository.one(Location.class, event.getLocationId()).getName())
				.replace("<jq:EXTRA_3 />", d));
		repository.save(chat);
	}
}