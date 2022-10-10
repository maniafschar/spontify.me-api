package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;

@Service
public class EventService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	public void findAndNotify() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch(
				"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
						"length(contact.attrInterest)>0 or length(contact.attrInterestEx)>0 or " +
						"length(contact.attr0)>0 or length(contact.attr0Ex)>0 or " +
						"length(contact.attr1)>0 or length(contact.attr1Ex)>0 or " +
						"length(contact.attr2)>0 or length(contact.attr2Ex)>0 or " +
						"length(contact.attr3)>0 or length(contact.attr3Ex)>0 or " +
						"length(contact.attr4)>0 or length(contact.attr4Ex)>0 or " +
						"length(contact.attr5)>0 or length(contact.attr5Ex)>0)");
		params.setLimit(0);
		final Result ids = repository.list(params);
		params.setQuery(Query.location_listEventCurrent);
		params.setDistance(50);
		params.setSearch("TO_DAYS(event.startDate)-1<=TO_DAYS(current_timestamp)");
		final LocalDate now = LocalDate.now(ZoneId.systemDefault());
		for (int i = 0; i < ids.size(); i++) {
			params.setUser(repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")));
			final Result events = repository.list(params);
			for (int i2 = 0; i2 < events.size(); i2++) {
				final Event event = repository.one(Event.class, (BigInteger) events.get(i2).get("event.id"));
				if (!params.getUser().getId().equals(event.getContactId())) {
					final LocalDate realDate = getRealDate(event, now);
					final Contact contactEvent = repository.one(Contact.class, event.getContactId());
					if (realDate.isAfter(now) && realDate.minusDays(1).isBefore(now)
							&& !isMaxParticipants(event, realDate, params.getUser())
							&& Score.getContact(contactEvent, params.getUser()) > 0.3 &&
							notificationService.sendNotification(contactEvent,
									params.getUser(), ContactNotificationTextType.eventNotify,
									Strings.encodeParam("e=" + event.getId()),
									(String) events.get(i2).get("location.name")))
						break;
				}
			}
		}
	}

	public void notifyParticipation() throws Exception {
		final QueryParams params = new QueryParams(Query.event_participate);
		params.setSearch("eventParticipate.eventDate>'" + Instant.now().minus((Duration.ofDays(1)))
				+ "' and eventParticipate.eventDate<'"
				+ Instant.now().plus(Duration.ofDays(1)) + "'");
		params.setLimit(0);
		final Result ids = repository.list(params);
		final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
		for (int i = 0; i < ids.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) ids.get(i).get("eventParticipate.id"));
			final Event event = repository.one(Event.class, eventParticipate.getEventId());
			if (event == null)
				repository.delete(eventParticipate);
			else {
				final ZonedDateTime time = Instant.ofEpochMilli(event.getStartDate().getTime()).atZone(ZoneOffset.UTC);
				if (time.getHour() == 0 || time.getHour() > now.getHour() && time.getHour() < now.getHour() + 3) {
					final Contact contact = repository.one(Contact.class, eventParticipate.getContactId());
					final ZonedDateTime t = time.minus(Duration.ofMinutes(contact.getTimezoneOffset()));
					notificationService.sendNotification(repository.one(Contact.class, event.getContactId()),
							contact, ContactNotificationTextType.eventNotification,
							Strings.encodeParam("e=" + event.getId()),
							repository.one(Location.class, event.getLocationId()).getName(),
							t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute());
				}
			}
		}
	}

	private LocalDate getRealDate(Event event, LocalDate now) {
		LocalDate realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDate();
		if (!"o".equals(event.getType())) {
			while (realDate.isBefore(now)) {
				if ("w".equals(event.getType()))
					realDate = realDate.plusWeeks(1);
				else if ("w2".equals(event.getType()))
					realDate = realDate.plusWeeks(2);
				else if ("m".equals(event.getType()))
					realDate = realDate.plusMonths(1);
				else
					realDate = realDate.plusYears(1);
			}
		}
		return realDate;
	}

	private boolean isMaxParticipants(Event event, LocalDate date, Contact contact) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.contact_eventParticipateCount);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate='"
				+ date + "' and eventParticipate.state=1");
		return ((Number) repository.one(params).get("_c")).intValue() >= event.getMaxParticipants().intValue();
	}
}
