package com.jq.findapp.service;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.backend.events.ImportMunich;
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
	private ExternalService externalService;

	@Autowired
	private Text text;

	@Autowired
	private ImportMunich importMunich;

	public SchedulerResult findMatchingBuddies() {
		final SchedulerResult result = new SchedulerResult();
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
					"event.startDate>=current_timestamp and cast(TO_DAYS(event.startDate) as integer)-1<=cast(TO_DAYS(current_timestamp) as integer)");
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
		final SchedulerResult result = new SchedulerResult();
		try {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.state=1 and eventParticipate.eventDate>cast('"
					+ Instant.now().minus((Duration.ofDays(1)))
					+ "' as timestamp) and eventParticipate.eventDate<cast('"
					+ Instant.now().plus(Duration.ofDays(1)) + "' as timestamp)");
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

	@Async
	public void publish(final BigInteger id) throws Exception {
		final Event event = repository.one(Event.class, id);
		final Contact contact = repository.one(Contact.class, event.getContactId());
		final Location location = repository.one(Location.class, event.getLocationId());
		final String date = new SimpleDateFormat("d.M.yyyy H:mm").format(event.getStartDate());
		final JsonNode json = new ObjectMapper()
				.readTree(Attachment.resolve(repository.one(Client.class, contact.getClientId()).getStorage()));
		final String fbId = externalService.publishOnFacebook(contact.getClientId(),
				(location == null
						? text.getText(contact, TextId.event_fbWithoutLocation)
								.replace("{pseudonym}", contact.getPseudonym()).replace("{date}", date)
								.replace("{description}", event.getDescription())
						: date + "\n" + event.getDescription() + "\n\n" + location.getName() + "\n"
								+ location.getAddress())
						+ (json.has("publishingPostfix") ? "\n\n" + json.get("publishingPostfix").asText() : ""),
				"/rest/marketing/event/" + id);
		if (fbId != null) {
			event.setPublishId(fbId);
			repository.save(event);
		}
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
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate=cast('"
				+ date.toLocalDate() + "' as timestamp) and eventParticipate.state=1");
		return repository.list(params).size() >= event.getMaxParticipants().intValue();
	}

	public SchedulerResult importEvents() {
		final SchedulerResult result = new SchedulerResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.result = "Munich: " + importMunich.run(this, clientId);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult publishEvents() {
		final SchedulerResult result = new SchedulerResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.result = publishClient(clientId) + "\n" + publishUser() + " user events published";
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private String publishClient(final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate>cast('" + Instant.now().plus(Duration.ofHours(2))
				+ "' as timestamp) and event.startDate<cast('" + Instant.now().plus(Duration.ofHours(10))
				+ "' as timestamp) and event.contactId=" + clientId
				+ " and (event.image is not null or location.image is not null)"
				+ " and event.url is not null"
				+ " and event.repetition='o'"
				+ " and event.maxParticipants is null"
				+ " and event.publishId is null");
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++)
			publish((BigInteger) result.get(i).get("event.id"));
		return result.size() + " published";
	}

	private int publishUser() throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate>cast('" + Instant.now().plus(Duration.ofMinutes(10)) + "' as timestamp)"
				+ " and event.publish=true"
				+ " and event.publishId is null"
				+ " and (event.modifiedAt is null or event.modifiedAt<cast('"
				+ Instant.now().minus(Duration.ofMinutes(15)) + "' as timestamp))");
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++)
			publish((BigInteger) result.get(i).get("event.id"));
		return result.size();
	}

	public String get(final String url) {
		try (final InputStream in = new URI(url).toURL().openStream()) {
			return IOUtils.toString(in, StandardCharsets.UTF_8)
					.replace('\n', ' ')
					.replace('\r', ' ')
					.replace('\u0013', ' ')
					.replace('\u001c', ' ')
					.replace('\u001e', ' ');
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}
