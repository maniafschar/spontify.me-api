package com.jq.findapp.repository.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
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
		utils.createContact(BigInteger.ONE);
		final Contact contact = utils.createContact(new BigInteger("2"));
		contact.setClientId(BigInteger.ONE);
		repository.save(contact);
		final Event event = new Event();
		event.setContactId(contact.getId());
		event.setDescription("abc");
		event.setRepetition("o");
		event.setStartDate(new Timestamp(millis));
		event.setType(EventType.Location);

		// when
		repository.save(event);

		// then
		assertEquals(millis, event.getStartDate().getTime());
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setSearch("eventParticipate.eventId=" + event.getId());
		final Result result = repository.list(params);
		assertEquals(1, result.size());
		final Object eventDate = result.get(0).get("eventParticipate.eventDate");
		assertEquals(event.getEndDate().toString(), eventDate.toString());
		assertEquals("2024-06-06T22:00:00Z", event.getStartDate().toInstant().toString());
		assertEquals("2024-06-06", event.getEndDate().toString());
		assertEquals("2024-06-06", eventDate.toString());
	}
}