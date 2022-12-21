package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Service
public class EventService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	public String[] findMatchingSpontis() {
		final String[] result = new String[] { getClass().getSimpleName() + "/findMatchingSpontis", null };
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
							"(length(contact.attrInterest)>0 or length(contact.attrInterestEx)>0) and (" +
							"length(contact.attr0)>0 or length(contact.attr0Ex)>0 or " +
							"length(contact.attr1)>0 or length(contact.attr1Ex)>0 or " +
							"length(contact.attr2)>0 or length(contact.attr2Ex)>0 or " +
							"length(contact.attr3)>0 or length(contact.attr3Ex)>0 or " +
							"length(contact.attr4)>0 or length(contact.attr4Ex)>0 or " +
							"length(contact.attr5)>0 or length(contact.attr5Ex)>0))");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.location_listEventCurrent);
			params.setDistance(50);
			params.setSearch("TO_DAYS(event.startDate)-1<=TO_DAYS(current_timestamp)");
			final LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
			for (int i = 0; i < ids.size(); i++) {
				params.setUser(repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")));
				params.setLatitude(params.getUser().getLatitude());
				params.setLongitude(params.getUser().getLongitude());
				final Result events = repository.list(params);
				for (int i2 = 0; i2 < events.size(); i2++) {
					final Event event = repository.one(Event.class, (BigInteger) events.get(i2).get("event.id"));
					if (!params.getUser().getId().equals(event.getContactId())) {
						final LocalDateTime realDate = getRealDate(event, now);
						final Contact contactEvent = repository.one(Contact.class, event.getContactId());
						if (realDate.isAfter(now)
								&& realDate.minusDays(1).isBefore(now)
								&& !isMaxParticipants(event, realDate, params.getUser())
								&& Score.getContact(contactEvent, params.getUser()) > 0.4) {
							if (event.getLocationId() == null) {
								final ZonedDateTime time = Instant.ofEpochMilli(event.getStartDate().getTime())
										.atZone(ZoneOffset.UTC);
								final ZonedDateTime t = time
										.minus(Duration.ofMinutes(params.getUser().getTimezoneOffset()));
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotifyWithoutLocation,
										Strings.encodeParam("e=" + event.getId()),
										t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute(),
										Text.valueOf("category_verb" + event.getCategory())
												.getText(params.getUser().getLanguage()),
										event.getText());
							} else
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotify,
										Strings.encodeParam("e=" + event.getId()),
										(String) events.get(i2).get("location.name"));
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	public String[] notifyParticipation() {
		final String[] result = new String[] { getClass().getSimpleName() + "/notifyParticipation", null };
		try {
			final QueryParams params = new QueryParams(Query.location_eventParticipate);
			params.setSearch("eventParticipate.state=1 and eventParticipate.eventDate>'"
					+ Instant.now().minus((Duration.ofDays(1)))
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
					final ZonedDateTime time = Instant.ofEpochMilli(event.getStartDate().getTime())
							.atZone(ZoneOffset.UTC);
					if (time.getDayOfMonth() == now.getDayOfMonth() &&
							(time.getHour() == 0
									|| time.getHour() > now.getHour() && time.getHour() < now.getHour() + 3)) {
						if (event.getLocationId() != null) {
							if (repository.one(Location.class, event.getLocationId()) == null)
								repository.delete(event);
							else {
								final Contact contact = repository.one(Contact.class, eventParticipate.getContactId());
								final ZonedDateTime t = time.minus(Duration.ofMinutes(contact.getTimezoneOffset()));
								notificationService.sendNotification(
										repository.one(Contact.class, event.getContactId()),
										contact, ContactNotificationTextType.eventNotification,
										Strings.encodeParam("e=" + event.getId()),
										repository.one(Location.class, event.getLocationId()).getName(),
										t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute());
							}
						}
					}
				}
			}
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	public String[] notifyCheckInOut() {
		final String[] result = new String[] { getClass().getSimpleName() + "/notifyCheckInOut", null };
		try {
			new CheckInOut().exec(null);
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	public void notifyCheckInOut(BigInteger contactId) throws Exception {
		new CheckInOut().exec(contactId);
	}

	public LocalDateTime getRealDate(Event event) {
		return getRealDate(event, LocalDateTime.now(ZoneId.systemDefault()));
	}

	private LocalDateTime getRealDate(Event event, LocalDateTime now) {
		LocalDateTime realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime();
		if (!"o".equals(event.getType())) {
			while (realDate.isBefore(now)) {
				if ("w1".equals(event.getType()))
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

	private boolean isMaxParticipants(Event event, LocalDateTime date, Contact contact) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.contact_eventParticipateCount);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate='"
				+ date.toLocalDate() + "' and eventParticipate.state=1");
		return ((Number) repository.one(params).get("_c")).intValue() >= event.getMaxParticipants().intValue();
	}

	private class CheckInOut {
		private void exec(BigInteger contactId) throws Exception {
			final QueryParams params = new QueryParams(Query.location_eventParticipate);
			params.setSearch((contactId == null ? "" : "eventParticipate.contactId=" + contactId + " and ") +
					"event.marketingEvent=true and event.startDate>='"
					+ Instant.now().minus(Duration.ofHours(3)) + "' and event.startDate<='"
					+ Instant.now().plus(Duration.ofHours(1)) + "' and eventParticipate.state=1");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final Contact contact = repository.one(Contact.class,
						(BigInteger) result.get(i).get("event.contactId"));
				sendCheckInOut((BigInteger) result.get(i).get("event.id"),
						contact.getLatitude(), contact.getLongitude(), contactId != null);
			}
		}

		private void sendCheckInOut(BigInteger eventId, Float latitude, Float longitude, boolean fromLocationService)
				throws Exception {
			final QueryParams params = new QueryParams(Query.contact_chat);
			final Event event = repository.one(Event.class, eventId);
			final Instant eventTime = Instant.ofEpochMilli(event.getStartDate().getTime());
			if (latitude == null) {
				if (Instant.now().plus(Duration.ofMinutes(10)).isAfter(eventTime)) {
					params.setSearch("chat.contactId2=" + event.getContactId() + " and chat.textId='"
							+ Text.marketing_eventLocationFailed + "' and chat.createdAt>'"
							+ Instant.now().minus(Duration.ofDays(1)) + "'");
					if (repository.list(params).size() == 0)
						sendChat(event, Text.marketing_eventLocationFailed, 0);
				}
				return;
			}
			final Location location = repository.one(Location.class, event.getLocationId());
			final double distance = GeoLocationProcessor.distance(location.getLatitude(), location.getLongitude(),
					latitude, longitude);
			final double maxDistance = 0.1;
			if (shouldSendChat(Text.marketing_eventCheckedIn, event.getContactId())) {
				if (Instant.now().plus(Duration.ofMinutes(10)).isAfter(eventTime)) {
					if (fromLocationService && distance < maxDistance)
						sendChat(event, Text.marketing_eventCheckedIn, distance);
					else if (Instant.now().minus(Duration.ofMinutes(10)).isAfter(eventTime))
						sendChat(event, Text.marketing_eventCheckedInFailed, distance);
				}
			} else if (Instant.now().minus(Duration.ofHours(1)).isAfter(eventTime)) {
				if (fromLocationService && distance < maxDistance)
					sendChat(event, Text.marketing_eventCheckedOut, distance);
				else if (distance >= maxDistance)
					sendChat(event, Text.marketing_eventCheckedOutFailedDistance, distance);
				else
					sendChat(event, Text.marketing_eventCheckedOutFailedOpenApp, distance);
			}
		}

		private boolean shouldSendChat(Text text, BigInteger contactId) {
			final QueryParams params = new QueryParams(Query.contact_chat);
			params.setSearch("chat.contactId2=" + contactId +
					" and chat.textId='" + text +
					"' and chat.createdAt>'" + Instant.now().minus(Duration.ofHours(6)) + "'");
			return repository.list(params).size() == 0;
		}

		private void sendChat(Event event, Text text, double distance) throws Exception {
			if (shouldSendChat(text, event.getContactId())) {
				final String d;
				if (distance >= 1)
					d = String.format("%.1f", distance) + "km";
				else
					d = ((int) (distance * 1000.0)) + "m";
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
	}
}