package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.events.ImportMunich;
import com.jq.findapp.util.Entity;
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

	public static List<String> getAnswers(final JsonNode poll, long state) {
		final List<String> answers = new ArrayList<>();
		if (state < 0)
			state += 2 * Integer.MAX_VALUE - 2;
		for (int i = 0; Math.pow(2, i) <= state; i++) {
			if ((state & (long) Math.pow(2, i)) > 0)
				answers.add(poll.get("a").get(i).asText());
		}
		return answers;
	}

	@Cron
	public CronResult cronMatch() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
							"(length(contact.skills)>0 or length(contact.skillsText)>0))");
			params.setLimit(0);
			final Result ids = this.repository.list(params);
			params.setQuery(Query.event_listMatching);
			params.setDistance(50);
			params.setSearch("event.endDate>current_timestamp");
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				params.setUser(this.repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")));
				params.setLatitude(params.getUser().getLatitude());
				params.setLongitude(params.getUser().getLongitude());
				final Result events = this.repository.list(params);
				for (int i2 = 0; i2 < events.size(); i2++) {
					final Event event = this.repository.one(Event.class, (BigInteger) events.get(i2).get("event.id"));
					if (!params.getUser().getId().equals(event.getContactId())) {
						final Instant realDate = this.getRealDate(event);
						final Contact contactEvent = this.repository.one(Contact.class, event.getContactId());
						if (realDate.isAfter(Instant.now())
								&& realDate.minus(Duration.ofDays(1)).isBefore(Instant.now())
								&& !this.isMaxParticipants(event, realDate, params.getUser())
								&& Score.getContact(contactEvent, params.getUser()) > 0.4) {
							final ZonedDateTime t = realDate
									.atZone(TimeZone.getTimeZone(params.getUser().getTimezone()).toZoneId());
							final String time = t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute();
							if (event.getLocationId() == null)
								this.notificationService.sendNotification(contactEvent,
										params.getUser(), TextId.notification_eventNotifyWithoutLocation,
										Strings.encodeParam(
												"e=" + event.getId() + "_" + realDate.toString().substring(0, 10)),
										time, event.getDescription());
							else
								this.notificationService.sendNotification(contactEvent,
										params.getUser(), TextId.notification_eventNotify,
										Strings.encodeParam(
												"e=" + event.getId() + "_" + realDate.toString().substring(0, 10)),
										(realDate.atOffset(ZoneOffset.ofHours(0)).getDayOfYear() == Instant.now()
												.atOffset(ZoneOffset.ofHours(0)).getDayOfYear()
														? this.text.getText(params.getUser(), TextId.today)
														: this.text.getText(params.getUser(), TextId.tomorrow))
												+ " " + time,
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

	@Cron
	public CronResult cron() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.state=1 and eventParticipate.eventDate>cast('"
					+ Instant.now().minus((Duration.ofDays(1)))
					+ "' as timestamp) and eventParticipate.eventDate<cast('"
					+ Instant.now().plus(Duration.ofDays(1)) + "' as timestamp)");
			params.setLimit(0);
			final Result ids = this.repository.list(params);
			final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				final EventParticipate eventParticipate = this.repository.one(EventParticipate.class,
						(BigInteger) ids.get(i).get("eventParticipate.id"));
				final Event event = this.repository.one(Event.class, eventParticipate.getEventId());
				if (event == null)
					this.repository.delete(eventParticipate);
				else {
					final ZonedDateTime time = Instant.ofEpochMilli(event.getStartDate().getTime())
							.atZone(ZoneOffset.UTC);
					if (time.getDayOfMonth() == now.getDayOfMonth() &&
							(time.getHour() == 0
									|| time.getHour() > now.getHour() && time.getHour() < now.getHour() + 3)) {
						if (event.getLocationId() != null && event.getLocationId().compareTo(BigInteger.ZERO) > 0) {
							if (this.repository.one(Location.class, event.getLocationId()) == null)
								this.repository.delete(event);
							else {
								final Contact contact = this.repository.one(Contact.class,
										eventParticipate.getContactId());
								final ZonedDateTime t = event.getStartDate().toInstant()
										.atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId());
								this.notificationService.sendNotification(
										this.repository.one(Contact.class, event.getContactId()),
										contact, TextId.notification_eventNotification,
										Strings.encodeParam(
												"e=" + event.getId() + "_" + eventParticipate.getEventDate()),
										this.repository.one(Location.class, event.getLocationId()).getName(),
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

	private boolean publish(final Event event) {
		if (!Strings.isEmpty(event.getPublishId()))
			return false;
		final Instant startDate = this.getRealDate(event);
		if (startDate.isBefore(Instant.now()) || startDate.minus(Duration.ofDays(1)).isAfter(Instant.now()))
			return false;
		final Contact contact = this.repository.one(Contact.class, event.getContactId());
		final Location location = event.getLocationId() == null ? null
				: this.repository.one(Location.class, event.getLocationId());
		final String date = new SimpleDateFormat("d.M.yyyy H:mm").format(new Date(startDate.toEpochMilli()));
		try {
			final JsonNode json = Json
					.toNode(Attachment.resolve(this.repository.one(Client.class, contact.getClientId()).getStorage()));
			final String description;
			if (event.getType() == EventType.Poll)
				description = this.text.getText(contact, TextId.event_fbPoll)
						.replace("{pseudonym}", contact.getPseudonym()).replace("{date}", date)
						.replace("{question}", Json.toNode(event.getDescription()).get("q").asText());
			else if (location == null)
				description = this.text.getText(contact, TextId.event_fbWithoutLocation)
						.replace("{pseudonym}", contact.getPseudonym()).replace("{date}", date)
						.replace("{description}", event.getDescription());
			else
				description = date + "\n" + event.getDescription() + "\n\n" + location.getName() + "\n"
						+ location.getAddress();
			final String fbId = this.externalService.publishOnFacebook(contact.getClientId(),
					description + (json.has("publishingPostfix") ? "\n\n" +
							json.get("publishingPostfix").asText() : ""),
					"/rest/marketing/event/" + event.getId());
			if (fbId != null) {
				event.setPublishId(fbId);
				this.repository.save(event);
			}
			return true;
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public Instant getRealDate(final Event event) {
		Instant realDate = Instant.ofEpochMilli(event.getStartDate().getTime());
		if (event.getRepetition() != Repetition.Once && event.getRepetition() != Repetition.Games) {
			while (realDate.isBefore(Instant.now())) {
				if (event.getRepetition() == Repetition.Week)
					realDate = realDate.plus(Duration.ofDays(7));
				else if (event.getRepetition() == Repetition.TwoWeeks)
					realDate = realDate.plus(Duration.ofDays(14));
				else {
					final OffsetDateTime d = realDate.atOffset(java.time.ZoneOffset.ofHours(0));
					final YearMonth yearMonth = YearMonth.of(d.getYear(), d.getMonthValue());
					realDate = realDate
							.plus(Duration.ofDays(event.getRepetition() == Repetition.Month ? yearMonth.lengthOfMonth()
									: yearMonth.lengthOfYear()));
				}
			}
		}
		return realDate;
	}

	private boolean isMaxParticipants(final Event event, final Instant date, final Contact contact) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setUser(contact);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate=cast('"
				+ date + "' as date) and eventParticipate.state=1");
		return this.repository.list(params).size() >= event.getMaxParticipants().intValue();
	}

	@Cron("40 5")
	public CronResult cronImport() {
		final CronResult result = new CronResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.body = "Munich: ";// + this.importMunich.run(this, clientId);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Cron
	public CronResult cronPublish() {
		final CronResult result = new CronResult();
		try {
			final BigInteger clientId = BigInteger.ONE;
			result.body = this.publishClient(clientId) + " client\n" + this.publishUser() + " user";
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Cron("40 23")
	public CronResult cronSeries() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.event_listId);
			params.setSearch("event.repetition='" + Repetition.Games.name()
					+ "' and event.startDate is null and event.skills like '9.%'");
			final Result events = this.repository.list(params);
			events.forEach(e -> {
				final Event event = this.repository.one(Event.class, (BigInteger) e.get("event.id"));
				event.setDescription(event.getDescription() + " ");
				this.repository.save(event);
			});
			result.body = events.size() + " initialized\n";
			final Set<String> processed = new HashSet<>();
			params.setSearch("event.repetition='" + Repetition.Games.name()
					+ "' and event.skills like '9.%' and event.skills not like '%X%'");
			final AtomicInteger updated = new AtomicInteger();
			this.repository.list(params).forEach(e -> {
				final String key = e.get("event.contactId") + "-"
						+ e.get("event.locationId") + "-"
						+ e.get("event.skills");
				if (!processed.contains(key)) {
					processed.add(key);
					if (this.updateSeries(this.repository.one(Event.class, (BigInteger) e.get("event.id"))) > 0)
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

	@Cron("* 8,9,10,11,12,13,14,15,16,17,18,19,20")
	public CronResult cronMarketing() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.misc_listClient);
		final Result list = this.repository.list(params);
		params.setQuery(Query.event_listId);
		for (int i = 0; i < list.size(); i++) {
			final Client client = this.repository.one(Client.class, (BigInteger) list.get(i).get("client.id"));
			final JsonNode node = Json.toNode(Attachment.resolve(client.getStorage()));
			if (node.has("marketing")) {
				for (int i2 = 0; i2 < node.get("marketing").size(); i2++) {
					if (Math.random() > 0.5) {
						final long contactId = node.get("marketing").get(i2).get("user").asLong();
						params.setSearch(
								"event.contactId=" + contactId + " and event.type='Inquiry' and event.startDate>=cast('"
										+ Instant.now().minus(Duration.ofHours(LocalDateTime.now().getHour()))
												.toString()
												.substring(0, 19)
										+ "' as timestamp)");
						final Result events = this.repository.list(params);
						if (events.size() == 0) {
							final JsonNode text = node.get("marketing").get(i2).get("text");
							final LocalDate date = LocalDate.now();
							final Event event = new Event();
							event.setContactId(BigInteger.valueOf(contactId));
							event.setDescription(text.get((int) (Math.random() * text.size() % text.size())).asText());
							event.setStartDate(new Timestamp(Instant
									.parse(date.getYear() + "-" + (date.getMonthValue() < 10 ? "0" : "")
											+ date.getMonthValue()
											+ "-" + (date.getDayOfMonth() < 10 ? "0" : "") + date.getDayOfMonth() + "T"
											+ (int) (19 + Math.random() * 2) + (":"
													+ ((int) (Math.random() * 4) % 4) * 15).replace(":0", ":00")
											+ ":00.00Z")
									.toEpochMilli()));
							event.setPublish(true);
							event.setLatitude(48.1330090f);
							event.setLongitude(11.56684f);
							event.setType(EventType.Inquiry);
							this.repository.save(event);
							result.body += client.getId() + "\n";
							break;
						} else {
							final Event event = this.repository.one(Event.class,
									(BigInteger) events.get(0).get("event.id"));
							if (Instant
									.ofEpochMilli((event.getModifiedAt() == null ? event.getCreatedAt()
											: event.getModifiedAt()).getTime())
									.plus(Duration.ofHours(4))
									.isBefore(Instant.now())) {
								event.setPublishId(null);
								if (this.publish(event)) {
									result.body += client.getId() + "\n";
									break;
								}
							}
						}
					}
				}
			}
		}
		return result;
	}

	private int publishClient(final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate>cast('" + Instant.now().plus(Duration.ofHours(2))
				+ "' as timestamp) and event.startDate<cast('" + Instant.now().plus(Duration.ofHours(10))
				+ "' as timestamp) and event.contactId=" + clientId
				+ " and (event.image is not null or location.image is not null)"
				+ " and event.url is not null"
				+ " and event.repetition='Once'"
				+ " and event.maxParticipants is null"
				+ " and event.publishId is null");
		final Result result = this.repository.list(params);
		final StringBuffer s = new StringBuffer();
		result.forEach(e -> {
			if (this.publish(this.repository.one(Event.class, (BigInteger) e.get("event.id"))))
				s.append("x");
		});
		return s.length();
	}

	private int publishUser() throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate>cast('" + Instant.now().plus(Duration.ofMinutes(10)) + "' as timestamp)"
				+ " and event.publish=true"
				+ " and event.publishId is null"
				+ " and (event.modifiedAt is null or event.modifiedAt<cast('"
				+ Instant.now().minus(Duration.ofMinutes(15)) + "' as timestamp))");
		final Result result = this.repository.list(params);
		final StringBuffer s = new StringBuffer();
		result.forEach(e -> {
			if (this.publish(this.repository.one(Event.class, (BigInteger) e.get("event.id"))))
				s.append("x");
		});
		return s.length();
	}

	public String get(final String url) {
		return Strings.urlContent(url)
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
				final List<FutureEvent> futureEvents = this.matchDayService
						.futureEvents(Integer.valueOf(event.getSkills().substring(2)));
				if (!futureEvents.isEmpty()) {
					final FutureEvent futureEvent = futureEvents.get(0);
					event.setStartDate(new Timestamp(futureEvent.time - EventListener.SERIES_TIMELAP));
					event.setSeriesId(futureEvent.time);
					event.setDescription(futureEvent.subject + "\n" + event.getDescription().trim());
					this.repository.save(event);
				} else
					return 0;
			}
			final List<FutureEvent> futureEvents = this.matchDayService
					.futureEvents(Integer.valueOf(event.getSkills().substring(2)));
			final String description = event.getDescription().contains("\n")
					? event.getDescription().substring(event.getDescription().indexOf("\n"))
					: "\n" + event.getDescription();
			final Contact contact = this.repository.one(Contact.class, event.getContactId());
			final String storage = contact.getStorage();
			final ObjectNode imported = Strings.isEmpty(storage) ? Json.createObject()
					: (ObjectNode) Json.toNode(Attachment.resolve(storage));
			final ArrayNode importedIds;
			if (imported.has("eventSeries"))
				importedIds = (ArrayNode) imported.get("eventSeries");
			else
				importedIds = imported.putArray("eventSeries");
			int count = 0;
			for (final FutureEvent futureEvent : futureEvents) {
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
					this.repository.save(e);
					count++;
					importedIds.add(event.getSkills() + '.' + futureEvent.time);
				}
			}
			this.repository.executeUpdate("update Event event set event.repetition='" + Repetition.Games.name()
					+ "' where event.repetition='" + Repetition.Once.name() + "' and event.contactId="
					+ event.getContactId() + " and event.skills='" + event.getSkills()
					+ "' and event.locationId=" + event.getLocationId());
			if (!importedIds.toString().contains('"' + event.getSkills() + '.' + event.getSeriesId() + '"'))
				importedIds.add(event.getSkills() + '.' + event.getSeriesId());
			contact.setStorage(Json.toString(imported));
			this.repository.save(contact);
			return count;
		}
		return 0;
	}

	public BigInteger importLocation(Location location, String image) throws Exception {
		if (!Strings.isEmpty(location.getAddress())) {
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("location.name like '" + location.getName().replace("'", "_")
					+ "' and '" + location.getAddress().toLowerCase().replace('\n', ' ')
					+ "' like concat('%',LOWER(location.zipCode),'%')");
			final Result list = repository.list(params);
			if (list.size() > 0)
				location = this.repository.one(Location.class, (BigInteger) list.get(0).get("location.id"));
			else {
				try {
					repository.save(location);
				} catch (final IllegalArgumentException ex) {
					if (ex.getMessage().contains("location exists"))
						location = this.repository.one(Location.class, new BigInteger(
								ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim()));
					else
						throw ex;
				}
			}
			if (!Strings.isEmpty(image) && Strings.isEmpty(location.getImage())) {
				location.setImage(Entity.getImage(image, Entity.IMAGE_SIZE, 250));
				if (location.getImage() != null) {
					location.setImageList(Entity.getImage(image, Entity.IMAGE_THUMB_SIZE, 0));
					repository.save(location);
				}
			}
		}
		return location.getId();
	}
}
