package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Event.FutureEvent;
import com.jq.findapp.entity.Event.Repetition;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.repository.listener.EventListener;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Job;
import com.jq.findapp.service.events.ImportMunich;
import com.jq.findapp.util.Json;
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

	@Autowired
	private MatchDayService matchDayService;

	public static List<String> getAnswers(JsonNode poll, long state) {
		final List<String> answers = new ArrayList<>();
		if (state < 0)
			state += 2 * Integer.MAX_VALUE - 2;
		for (int i = 0; Math.pow(2, i) <= state; i++) {
			if ((state & (long) Math.pow(2, i)) > 0)
				answers.add(poll.get("a").get(i).asText());
		}
		return answers;
	}

	@Job
	public CronResult runMatch() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
							"(length(contact.skills)>0 or length(contact.skillsText)>0))");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.event_listMatching);
			params.setDistance(50);
			params.setSearch("event.endDate>current_timestamp");
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
										params.getUser(), TextId.notification_eventNotifyWithoutLocation,
										Strings.encodeParam("e=" + event.getId() + "_" + realDate.toLocalDate()),
										time,
										event.getDescription());
							else
								notificationService.sendNotification(contactEvent,
										params.getUser(), TextId.notification_eventNotify,
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
			result.body = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Job
	public CronResult run() {
		final CronResult result = new CronResult();
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
										contact, TextId.notification_eventNotification,
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
			result.body = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private void publish(final BigInteger id) {
		final Event event = repository.one(Event.class, id);
		if (!Strings.isEmpty(event.getPublishId()))
			return;
		final Contact contact = repository.one(Contact.class, event.getContactId());
		final Location location = event.getLocationId() == null ? null
				: repository.one(Location.class, event.getLocationId());
		final String date = new SimpleDateFormat("d.M.yyyy H:mm").format(event.getStartDate());
		try {
			final JsonNode json = new ObjectMapper()
					.readTree(Attachment.resolve(repository.one(Client.class, contact.getClientId()).getStorage()));
			final String description;
			if (event.getType() == EventType.Poll)
				description = text.getText(contact, TextId.event_fbPoll)
						.replace("{pseudonym}", contact.getPseudonym()).replace("{date}", date)
						.replace("{question}", Json.toNode(event.getDescription()).get("q").asText());
			else if (location == null)
				description = text.getText(contact, TextId.event_fbWithoutLocation)
						.replace("{pseudonym}", contact.getPseudonym()).replace("{date}", date)
						.replace("{description}", event.getDescription());
			else
				description = date + "\n" + event.getDescription() + "\n\n" + location.getName() + "\n" + location.getAddress();
			final String fbId = externalService.publishOnFacebook(contact.getClientId(),
					description + (json.has("publishingPostfix") ? "\n\n" + json.get("publishingPostfix").asText() : ""),
					"/rest/marketing/event/" + id);
			if (fbId != null) {
				event.setPublishId(fbId);
				repository.save(event);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public LocalDateTime getRealDate(final Event event) {
		return getRealDate(event, LocalDateTime.now(ZoneId.systemDefault()));
	}

	private LocalDateTime getRealDate(final Event event, final LocalDateTime now) {
		LocalDateTime realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime();
		if (event.getRepetition() != Repetition.Once && event.getRepetition() != Repetition.Games) {
			while (realDate.isBefore(now)) {
				if (event.getRepetition() == Repetition.Week)
					realDate = realDate.plusWeeks(1);
				else if (event.getRepetition() == Repetition.TwoWeeks)
					realDate = realDate.plusWeeks(2);
				else if (event.getRepetition() == Repetition.Month)
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

	@Job(cron = "40 5")
	public CronResult runImport() {
		final CronResult result = new CronResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.body = "Munich: " + importMunich.run(this, clientId);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Job
	public CronResult runPublish() {
		final CronResult result = new CronResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.body = publishClient(clientId) + "\n" + publishUser() + " user events published";
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Job(cron = "40 23")
	public CronResult runSeries() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.event_listId);
			params.setSearch("event.repetition='" + Repetition.Games.name()
					+ "' and event.startDate is null and event.skills like '9.%'");
			final Result events = repository.list(params);
			events.forEach(e -> {
				final Event event = repository.one(Event.class, (BigInteger) e.get("event.id"));
				event.setDescription(event.getDescription() + " ");
				repository.save(event);
			});
			result.body = events.size() + " initialized\n";
			final Set<String> processed = new HashSet<>();
			params.setSearch("event.repetition='" + Repetition.Games.name()
					+ "' and event.skills like '9.%' and event.skills not like '%X%'");
			final AtomicInteger updated = new AtomicInteger();
			repository.list(params).forEach(e -> {
				final String key = e.get("event.contactId") + "-"
						+ e.get("event.locationId") + "-"
						+ e.get("event.skills");
				if (!processed.contains(key)) {
					processed.add(key);
					if (updateSeries(repository.one(Event.class, (BigInteger) e.get("event.id"))) > 0)
						updated.incrementAndGet();
				}
			});
			if (updated.get() > 0)
				result.body += updated.get() + " updated";
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
				+ " and event.repetition='Once'"
				+ " and event.maxParticipants is null"
				+ " and event.publishId is null");
		final Result result = repository.list(params);
		result.forEach(e -> {
			publish((BigInteger) e.get("event.id"));
		});
		return result.size() + " published";
	}

	private int publishUser() throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("(event.type='Poll' or event.startDate>cast('"
				+ Instant.now().plus(Duration.ofMinutes(10)) + "' as timestamp))"
				+ " and event.publish=true"
				+ " and event.publishId is null"
				+ " and (event.modifiedAt is null or event.modifiedAt<cast('"
				+ Instant.now().minus(Duration.ofMinutes(15)) + "' as timestamp))");
		final Result result = repository.list(params);
		result.forEach(e -> publish((BigInteger) e.get("event.id")));
		return result.size();
	}

	public String get(final String url) {
		return Strings.url2string(url)
				.replace('\n', ' ')
				.replace('\r', ' ')
				.replace('\u0013', ' ')
				.replace('\u001c', ' ')
				.replace('\u001e', ' ');
	}

	public int updateSeries(final Event event) {
		if (event.getRepetition() == Repetition.Games && !Strings.isEmpty(event.getSkills())
				&& event.getSkills().startsWith("9.") && event.getLocationId() != null
				&& !event.getSkills().contains("X")) {
			if (event.getSeriesId() == null) {
				final List<FutureEvent> futureEvents = matchDayService
						.futureEvents(Integer.valueOf(event.getSkills().substring(2)));
				if (!futureEvents.isEmpty()) {
					final FutureEvent futureEvent = futureEvents.get(0);
					event.setStartDate(new Timestamp(futureEvent.time - EventListener.SERIES_TIMELAP));
					event.setSeriesId(futureEvent.time);
					event.setDescription(futureEvent.subject + "\n" + event.getDescription().trim());
					repository.save(event);
				} else
					return 0;
			}
			final List<FutureEvent> futureEvents = matchDayService
					.futureEvents(Integer.valueOf(event.getSkills().substring(2)));
			final String description = event.getDescription().contains("\n")
					? event.getDescription().substring(event.getDescription().indexOf("\n"))
					: "\n" + event.getDescription();
			final Contact contact = repository.one(Contact.class, event.getContactId());
			final String storage = contact.getStorage();
			final ObjectNode imported = Strings.isEmpty(storage) ? Json.createObject()
					: (ObjectNode) Json.toNode(Attachment.resolve(storage));
			final ArrayNode importedIds;
			if (imported.has("eventSeries"))
				importedIds = (ArrayNode) imported.get("eventSeries");
			else
				importedIds = imported.putArray("eventSeries");
			int count = 0;
			for (FutureEvent futureEvent : futureEvents) {
				if (futureEvent.time > event.getSeriesId()
						&& !importedIds.toString().contains('"' + event.getSkills() + '.' + futureEvent.time + '"')) {
					final Event e = new Event();
					e.setContactId(event.getContactId());
					e.setDescription(futureEvent.subject + description);
					e.setImage(event.getImage());
					e.setImageList(event.getImageList());
					e.setLocationId(event.getLocationId());
					e.setMaxParticipants(event.getMaxParticipants());
					e.setPrice(event.getPrice());
					e.setPublish(event.getPublish());
					e.setSeriesId(futureEvent.time);
					e.setSkills(event.getSkills());
					e.setSkillsText(event.getSkillsText());
					e.setStartDate(new Timestamp(futureEvent.time - EventListener.SERIES_TIMELAP));
					e.setType(event.getType());
					e.setUrl(event.getUrl());
					repository.save(e);
					count++;
					importedIds.add(event.getSkills() + '.' + futureEvent.time);
				}
			}
			repository.executeUpdate("update Event event set event.repetition='" + Repetition.Games.name()
					+ "' where event.repetition='" + Repetition.Once.name() + "' and event.contactId="
					+ event.getContactId() + " and event.skills='" + event.getSkills()
					+ "' and event.locationId=" + event.getLocationId());
			if (!importedIds.toString().contains('"' + event.getSkills() + '.' + event.getSeriesId() + '"'))
				importedIds.add(event.getSkills() + '.' + event.getSeriesId());
			contact.setStorage(Json.toString(imported));
			repository.save(contact);
			return count;
		}
		return 0;
	}
}
