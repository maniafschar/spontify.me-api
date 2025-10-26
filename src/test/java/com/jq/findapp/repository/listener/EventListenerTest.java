package com.jq.findapp.repository.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Event.Repetition;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EventListenerTest {
	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void save_participation() throws Exception {
		// given
		final long millis = 1717711200000l;
		this.utils.createContact(BigInteger.ONE);
		final Contact contact = this.utils.createContact(new BigInteger("2"));
		contact.setClientId(BigInteger.ONE);
		this.repository.save(contact);
		final Event event = new Event();
		event.setContactId(contact.getId());
		event.setDescription("abc");
		event.setRepetition(Repetition.Once);
		event.setStartDate(new Timestamp(millis));
		event.setType(EventType.Location);

		// when
		this.repository.save(event);

		// then
		assertEquals(millis, event.getStartDate().getTime());
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = this.repository.list(params);
		assertEquals(1, result.size());
		final Object eventDate = result.get(0).get("eventParticipate.eventDate");
		assertEquals(event.getEndDate().toString(), eventDate.toString());
		assertEquals("2024-06-06T22:00:00Z", event.getStartDate().toInstant().toString());
		assertEquals("2024-06-06", event.getEndDate().toString());
		assertEquals("2024-06-06", eventDate.toString());
	}

	@Test
	public void save_series() throws Exception {
		// given
		final long now = System.currentTimeMillis();
		final Contact contact = this.utils.createContact(new BigInteger("99"));
		final Event event = new Event();
		event.setContactId(contact.getId());
		event.setLocationId(BigInteger.ZERO);
		event.setDescription("abc");
		event.setRepetition(Repetition.Games);
		event.setSkills("9.157");
		event.setType(EventType.Location);

		// when
		this.repository.save(event);

		// then
		assertNotNull(event.getStartDate());
		assertNotNull(event.getSeriesId() >= now);
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.contactId=" + contact.getId());
		final Result result = this.repository.list(params);
		assertEquals(3, result.size());
		final Event last = this.repository.one(Event.class, (BigInteger) result.get(result.size() - 1).get("event.id"));
		assertEquals(contact.getId(), last.getContactId());
		assertTrue(
				last.getDescription().endsWith(event.getDescription().substring(event.getDescription().indexOf("\n"))));
		assertEquals(event.getLocationId(), last.getLocationId());
		assertEquals(Repetition.Games, last.getRepetition());
	}

	@Test
	public void save_seriesTwice() throws Exception {
		// given
		final Event event = new Event();
		event.setContactId(this.utils.createContact(BigInteger.ONE).getId());
		event.setLocationId(BigInteger.ZERO);
		event.setDescription("abc");
		event.setRepetition(Repetition.Games);
		event.setSkills("9.157");
		event.setType(EventType.Location);
		this.repository.save(event);
		event.setId(null);

		// when
		try {
			this.repository.save(event);
			throw new RuntimeException("IllegalArgumentException expected");
		} catch (final IllegalArgumentException ex) {

			// then exact exception
			if (!ex.getMessage().startsWith("event series exists: "))
				throw new RuntimeException("wrong exception message: " + ex.getMessage());
		}
	}

	@Test
	public void save_seriesContactStorage() throws Exception {
		// given
		final Event event = new Event();
		event.setContactId(this.utils.createContact(BigInteger.valueOf(98l)).getId());
		event.setLocationId(BigInteger.ZERO);
		event.setDescription("abc");
		event.setRepetition(Repetition.Games);
		event.setSkills("9.157");
		event.setType(EventType.Location);
		this.repository.save(event);

		// when
		this.repository.save(event);

		// then
		final JsonNode storage = Json
				.toNode(Attachment.resolve(this.repository.one(Contact.class, BigInteger.ONE).getStorage()));
		assertTrue(storage.has("eventSeries"), storage.toPrettyString());
		assertEquals(3, storage.get("eventSeries").size());
	}
}