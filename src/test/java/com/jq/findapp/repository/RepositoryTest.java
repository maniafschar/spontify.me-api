package com.jq.findapp.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.ContactRating;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.util.Encryption;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class, JpaTestConfiguration.class })
@ActiveProfiles("test")
public class RepositoryTest {
	@Autowired
	private Repository repository;

	private Contact createContact() throws Exception {
		final Contact contact = new Contact();
		contact.setEmail("test@jq-consulting.de");
		contact.setLanguage("DE");
		contact.setIdDisplay("123456");
		contact.setFacebookId("1234567890");
		contact.setGender((short) 1);
		contact.setBirthday(new Date(3000000000L));
		contact.setPseudonym("pseudonym");
		contact.setVerified(true);
		contact.setVisitPage(new Timestamp(System.currentTimeMillis() - 3000000L));
		contact.setPassword(Encryption.encryptDB("test"));
		contact.setPasswordReset(System.currentTimeMillis());
		repository.save(contact);
		return contact;
	}

	private Chat createChat(final Contact contact) throws Exception {
		final Chat chat = new Chat();
		chat.setNote("Hi");
		chat.setContactId(contact.getId());
		chat.setContactId2(contact.getId());
		repository.save(chat);
		return chat;
	}

	private ContactRating createRatingContact(final Contact contact) throws Exception {
		final ContactRating rating = new ContactRating();
		rating.setText("Hi");
		rating.setContactId(contact.getId());
		rating.setContactId2(contact.getId());
		repository.save(rating);
		return rating;
	}

	@Test
	public void saveChat() throws Exception {
		// given
		final Contact contact = createContact();
		final Chat chat = createChat(contact);
		final ContactRating rating = createRatingContact(contact);
		final long created = chat.getCreatedAt().getTime();

		// when
		chat.setImage("20000/15603.jpeg");
		repository.save(chat);

		// then
		assertEquals(created, chat.getCreatedAt().getTime());
		assertEquals("Hi", chat.getNote());
		assertEquals("20000/15603.jpeg", chat.getImage());
		assertNotEquals(created, chat.getModifiedAt().getTime());
		assertNotNull(rating.getCreatedAt());
		assertNull(rating.getModifiedAt());
	}

	@Test
	public void saveContact() throws Exception {
		// given
		final Contact contact = createContact();
		final Timestamp visitPage = contact.getVisitPage();

		// when
		contact.setActive(true);
		contact.setLoginLink(null);
		contact.setOs(contact.getOs());
		contact.setDevice(contact.getDevice());
		contact.setVersion(contact.getVersion());
		contact.setVerified(true);
		repository.save(contact);

		// then
		assertEquals(visitPage, contact.getVisitPage());
	}

	@Test
	public void delete() throws Exception {
		// given
		final Contact contact = createContact();

		// when
		repository.deleteAccount(contact.getId());
	}

	@Test
	public void decode() {
		// given
		final Map<String, Object> map = new HashMap<>();
		map.put("contactId2", "213");

		// when
		final ContactBluetooth cb = new ObjectMapper().convertValue(map, ContactBluetooth.class);

		// then
		assertEquals(BigInteger.valueOf(213), cb.getContactId2());
	}

	@Test
	public void queries() throws Exception {
		// given
		final QueryParams params = new QueryParams(null);
		params.setUser(createContact());

		// when
		for (Query query : Query.values()) {
			params.setQuery(query);
			final Result result = repository.list(params);

			// then
			assertNotNull(result);
			assertNotNull(result.getHeader()[0]);
		}
	}

	@Test
	public void list() throws Exception {
		// given
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setUser(createContact());
		params.setSearch("contact.id=" + params.getUser().getId());

		// when
		final Result result = repository.list(params);

		// then
		assertEquals("contact.age", result.getHeader()[0]);
		assertEquals("1970-02-04", "" + result.get(0).get("contact.birthday"));
	}

	@Test
	public void queryGeoLocation() throws Exception {
		// given
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setDistance(100);
		params.setLatitude(12.0);
		params.setLongitude(49.8);
		params.setUser(createContact());

		// when
		final Result result = repository.list(params);

		// then
		assertNotNull(result);
		assertTrue(result.getHeader()[0] instanceof String);
	}

	@Test
	public void repositoryListener() throws Exception {
		// given
		final Contact contact = createContact();
		createContact();
		createContact();
		final Feedback feedback = new Feedback();
		feedback.setText("abc");
		feedback.setContactId(contact.getId());

		// when
		repository.save(feedback);

		// then
		assertNotNull(feedback.getId());
	}
}