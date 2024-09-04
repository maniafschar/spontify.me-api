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
	@Autowired
	private EventService eventService;

	@Autowired
	private SurveyService surveyService;

	@Override
	public void prePersist(final Event event) throws Exception {
		preUpdate(event);
	}

	@Override
	public void preUpdate(final Event event) throws Exception {
		if (event.getRepetition() == null)
			event.setRepetition("o");
		if ("o".equals(event.getRepetition()))
			event.setEndDate(getDate(event.getStartDate()));
	}

	@Override
	public void postPersist(final Event event) throws Exception {
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
	}

	private Date getDate(Timestamp timestamp) {
		final Instant instant = timestamp.toInstant();
		return new Date(instant.minus(Duration.ofHours(instant.atOffset(ZoneOffset.UTC).getHour())).toEpochMilli());
	}

	@Override
	public void postUpdate(final Event event) throws Exception {
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
					if ("o".equals(event.getRepetition()))
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
	}

	@Override
	public void postRemove(final Event event) throws Exception {
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

	public void updateSeries(final Event event) throws Exception {
		if (!Strings.isEmpty(event.getSkills())) {
			for (String skill : event.getSkills().split("\\|")) {
				if (skill.startsWith("9.")) {
					boolean canceled = event.getSkills().contains("X");
					final QueryParams params = new QueryParams(Query.event_listId);
					params.setSearch("event.contactId=" + event.getContactId() + " and cast(REGEXP_LIKE('" + skill
							+ "', event.skills) as integer)=1");
					final Result events = repository.list(params);
					if (!canceled) {
						for (int i = 0; i < events.size(); i++) {
							if (((String) events.get(i).get("event.skills")).contains("X")) {
								canceled = true;
								break;
							}
						}
					}
					if (canceled) {
						for (int i = 0; i < events.size(); i++) {
							if (!((String) events.get(i).get("event.skills")).contains("X") &&
									event.getId().compareTo((BigInteger) events.get(i).get("event.id")) != 0) {
								final Event e = repository.one(Event.class,
										(BigInteger) events.get(i).get("event.id"));
								e.setSkills(e.getSkills() + "|X");
								repository.save(e);
							}
						}
					} else
						updateFutureEvents(event, events, skill);
					break;
				}
			}
		}
	}

	private void updateFutureEvents(final Event event, final Result events, final String skill) throws Exception {
		final List<FutureEvent> futureEvents = surveyService.futureEvents(Integer.valueOf(skill.substring(2)));
		for (FutureEvent futureEvent : futureEvents) {
			boolean create = true;
			for (int i = 0; i < events.size(); i++) {
				if (event.getSeriesId() != null && event.getSeriesId() == futureEvent.time) {
					create = false;
					break;
				}
			}
			if (create) {
				final Event e = new Event();
				e.setContactId(event.getContactId());
				e.setDescription(event.getDescription());
				e.setImage(event.getImage());
				e.setImageList(event.getImageList());
				e.setLocationId(event.getLocationId());
				e.setMaxParticipants(event.getMaxParticipants());
				e.setPrice(event.getPrice());
				e.setPublish(event.getPublish());
				e.setSeriesId(futureEvent.time);
				e.setSkills(event.getSkills());
				e.setSkillsText(event.getSkillsText());
				e.setStartDate(new Timestamp(futureEvent.time - 30 * 60 * 1000));
				e.setType(event.getType());
				e.setUrl(event.getUrl());
				repository.save(e);
			}
		}
	}
}
