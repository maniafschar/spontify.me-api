package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class EventService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	public SchedulerResult findMatchingBuddies() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/findMatchingBuddies");
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
							"(length(contact.skills)>0 or length(contact.skillsText)>0))");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.event_listMatching);
			params.setDistance(50);
			params.setSearch(
					"event.startDate>=current_timestamp and TO_DAYS(event.startDate)-1<=TO_DAYS(current_timestamp)");
			final LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
			int count = 0;
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
							final ZonedDateTime t = realDate
									.atZone(TimeZone.getTimeZone(params.getUser().getTimezone()).toZoneId());
							final String time = t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute();
							if (event.getLocationId() == null)
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotifyWithoutLocation,
										Strings.encodeParam("e=" + event.getId() + "_" + realDate.toLocalDate()),
										time,
										event.getDescription());
							else
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotify,
										Strings.encodeParam("e=" + event.getId() + "_" + realDate.toLocalDate()),
										(realDate.getDayOfYear() == now.getDayOfYear()
												? text.getText(params.getUser(), TextId.today)
												: text.getText(params.getUser(), TextId.tomorrow)) + " " + time,
										(String) events.get(i2).get("location.name"));
							count++;
							break;
						}
					}
				}
			}
			result.result = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult notifyParticipation() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/notifyParticipation");
		try {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.state=1 and eventParticipate.eventDate>'"
					+ Instant.now().minus((Duration.ofDays(1)))
					+ "' and eventParticipate.eventDate<'"
					+ Instant.now().plus(Duration.ofDays(1)) + "'");
			params.setLimit(0);
			final Result ids = repository.list(params);
			final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
			int count = 0;
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
						if (event.getLocationId() != null && event.getLocationId().compareTo(BigInteger.ZERO) > 0) {
							if (repository.one(Location.class, event.getLocationId()) == null)
								repository.delete(event);
							else {
								final Contact contact = repository.one(Contact.class, eventParticipate.getContactId());
								final ZonedDateTime t = event.getStartDate().toInstant()
										.atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId());
								notificationService.sendNotification(
										repository.one(Contact.class, event.getContactId()),
										contact, ContactNotificationTextType.eventNotification,
										Strings.encodeParam(
												"e=" + event.getId() + "_" + eventParticipate.getEventDate()),
										repository.one(Location.class, event.getLocationId()).getName(),
										t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute());
								count++;
							}
						}
					}
				}
			}
			result.result = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public LocalDateTime getRealDate(final Event event) {
		return getRealDate(event, LocalDateTime.now(ZoneId.systemDefault()));
	}

	private LocalDateTime getRealDate(final Event event, final LocalDateTime now) {
		LocalDateTime realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime();
		if (!"o".equals(event.getRepetition())) {
			while (realDate.isBefore(now)) {
				if ("w1".equals(event.getRepetition()))
					realDate = realDate.plusWeeks(1);
				else if ("w2".equals(event.getRepetition()))
					realDate = realDate.plusWeeks(2);
				else if ("m".equals(event.getRepetition()))
					realDate = realDate.plusMonths(1);
				else
					realDate = realDate.plusYears(1);
			}
		}
		return realDate;
	}

	private boolean isMaxParticipants(final Event event, final LocalDateTime date, final Contact contact) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setUser(contact);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate='"
				+ date.toLocalDate() + "' and eventParticipate.state=1");
		return repository.list(params).size() >= event.getMaxParticipants().intValue();
	}
}