package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Event.Repetition;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.backend.SurveyService;
import com.jq.findapp.service.backend.SurveyService.FutureEvent;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text.TextId;

@Component
public class EventListener extends AbstractRepositoryListener<Event> {
	public static final long SERIES_TIMELAP = 30 * 60 * 1000;

	@Autowired
	private EventService eventService;

	@Autowired
	private SurveyService surveyService;

	@Override
	public void prePersist(final Event event) {
		preUpdate(event);
		if (event.getRepetition() == Repetition.Games) {
			if (event.getSeriesId() == null) {
				final QueryParams params = new QueryParams(Query.event_listId);
				params.setSearch("event.contactId=" + event.getContactId()
						+ " and length(event.skills)>0 and cast(REGEXP_LIKE('" + event.getSkills()
						+ "', event.skills) as integer)=1 and event.repetition='" + Repetition.Games.name()
						+ "' and event.startDate>cast('" + Instant.now() + "'' as timestamp)");
				final Result events = repository.list(params);
				if (events.size() > 0) {
					long max = Long.MAX_VALUE;
					Object id = null;
					for (int i = 0; i < events.size(); i++) {
						if (max > ((Timestamp) events.get(i).get("event.startDate")).getTime()) {
							max = ((Timestamp) events.get(i).get("event.startDate")).getTime();
							id = events.get(i).get("event.id");
						}
					}
					throw new IllegalArgumentException("event series exists: " + id);
				}
			}
			final List<FutureEvent> futureEvents = surveyService
					.futureEvents(Integer.valueOf(event.getSkills().substring(2)));
			if (!futureEvents.isEmpty()) {
				final FutureEvent futureEvent = futureEvents.get(0);
				event.setStartDate(new Timestamp(futureEvent.time - SERIES_TIMELAP));
				event.setSeriesId(futureEvent.time);
				event.setDescription(futureEvent.subject + "\n" + event.getDescription());
			}
		}
	}

	@Override
	public void preUpdate(final Event event) {
		if (event.getRepetition() == null)
			event.setRepetition(Repetition.Once);
		if (event.getRepetition() == Repetition.Once || event.getRepetition() == Repetition.Games)
			event.setEndDate(getDate(event.getStartDate()));
	}

	@Override
	public void postPersist(final Event event) {
		if (event.getType() != EventType.Poll &&
				!repository.one(Client.class, repository.one(Contact.class, event.getContactId()).getClientId())
						.getAdminId().equals(event.getContactId())) {
			final EventParticipate eventParticipate = new EventParticipate();
			eventParticipate.setState(1);
			eventParticipate.setContactId(event.getContactId());
			eventParticipate.setEventId(event.getId());
			eventParticipate.setEventDate(getDate(event.getStartDate()));
			repository.save(eventParticipate);
		}
		eventService.updateSeries(event);
	}

	private Date getDate(Timestamp timestamp) {
		if (timestamp == null)
			return null;
		final Instant instant = timestamp.toInstant();
		return new Date(instant.minus(Duration.ofHours(instant.atOffset(ZoneOffset.UTC).getHour())).toEpochMilli());
	}

	@Override
	public void postUpdate(final Event event) {
		if (event.old("startDate") != null || event.old("price") != null) {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch(
					"eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate>=cast('"
							+ Instant.now().minus((Duration.ofDays(1))) + "' as timestamp)");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) result.get(i).get("eventParticipate.id"));
				if (event.old("startDate") != null) {
					if (event.getRepetition() == Repetition.Once || event.getRepetition() == Repetition.Games)
						eventParticipate
								.setEventDate(new java.sql.Date(event.getStartDate().toInstant().toEpochMilli()));
					else
						eventParticipate
								.setEventDate(new java.sql.Date(
										eventService.getRealDate(event).toInstant(ZoneOffset.UTC).toEpochMilli()));
					repository.save(eventParticipate);
				}
				if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
					final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
					if (time.isAfter(Instant.now())) {
						final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
						final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
						final Contact contactFrom = repository.one(Contact.class, event.getContactId());
						notificationService.sendNotification(contactFrom, contactTo,
								TextId.notification_eventChanged,
								Strings.encodeParam("e=" + event.getId() + "_" + eventParticipate.getEventDate()),
								Strings.formatDate(null,
										new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60)
												.toEpochMilli()),
										contactTo.getTimezone()),
								event.getDescription(),
								repository.one(Location.class, event.getLocationId()).getName());
					}
				}
			}
		}
		eventService.updateSeries(event);
	}

	@Override
	public void postRemove(final Event event) {
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		if (result.size() > 0 && event.getPrice() != null && event.getPrice() > 0)
			throw new RuntimeException("Paid events with participants cannot be deleted");
		for (int i = 0; i < result.size(); i++) {
			final EventParticipate eventParticipate = repository.one(EventParticipate.class,
					(BigInteger) result.get(i).get("eventParticipate.id"));
			if (eventParticipate.getState() == 1 && !eventParticipate.getContactId().equals(event.getContactId())) {
				final Instant time = new Timestamp(eventParticipate.getEventDate().getTime()).toInstant();
				if (time.isAfter(Instant.now())) {
					final ZonedDateTime t = event.getStartDate().toInstant().atZone(ZoneOffset.UTC);
					final Contact contactTo = repository.one(Contact.class, eventParticipate.getContactId());
					final Contact contactFrom = repository.one(Contact.class, eventParticipate.getContactId());
					notificationService.sendNotification(contactFrom, contactTo,
							TextId.notification_eventDelete,
							Strings.encodeParam("p=" + contactFrom.getId()),
							Strings.formatDate(null,
									new Date(time.plusSeconds((t.getHour() * 60 + t.getMinute()) * 60).toEpochMilli()),
									contactTo.getTimezone()),
							event.getDescription(),
							repository.one(Location.class, event.getLocationId()).getName());
				}
			}
			repository.delete(eventParticipate);
		}
	}

}
