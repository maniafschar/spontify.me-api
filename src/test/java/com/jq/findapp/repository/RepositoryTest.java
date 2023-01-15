package com.jq.findapp.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactBluetooth;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Utils;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class, JpaTestConfiguration.class })
@ActiveProfiles("test")
public class RepositoryTest {
	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private Chat createChat(final Contact contact) throws Exception {
		final Chat chat = new Chat();
		chat.setNote("Hi");
		chat.setContactId(contact.getId());
		chat.setContactId2(adminId);
		repository.save(chat);
		return chat;
	}

	@Test
	public void saveChat() throws Exception {
		// given
		final Contact contact = utils.createContact();
		final Chat chat = createChat(contact);
		final long created = chat.getCreatedAt().getTime();
		final byte[] b = new byte[500];
		for (int i = 0; i < b.length; i++)
			b[i] = 24;
		chat.setImage(".jpg" + Attachment.SEPARATOR + Base64.getEncoder().encodeToString(b));

		// when
		repository.save(chat);

		// then
		assertEquals(created, chat.getCreatedAt().getTime());
		assertEquals("Hi", chat.getNote());
		assertTrue(chat.getImage().startsWith(".jpg" + Attachment.SEPARATOR));
		assertNotEquals(created, chat.getModifiedAt().getTime());
	}

	@Test
	public void saveContact() throws Exception {
		// given
		final Contact contact = utils.createContact();
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
		params.setUser(utils.createContact());

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
		params.setUser(utils.createContact());
		params.setSearch("contact.id=" + params.getUser().getId());

		// when
		final Result result = repository.list(params);

		// then
		assertEquals("contact.age", result.getHeader()[0]);
		assertEquals("1970-02-04", "" + result.get(0).get("contact.birthday"));
	}

	@Test
	public void queryContactList() throws Exception {
		// given
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setDistance(100);
		params.setLatitude(12.0f);
		params.setLongitude(49.8f);
		params.setUser(utils.createContact());

		// when
		final Result result = repository.list(params);

		// then
		assertNotNull(result);
		assertTrue(result.getHeader()[0] instanceof String);
	}

	@Test
	public void queryGeoLocation() throws Exception {
		// given
		final GeoLocation geoLocation = new GeoLocation();
		geoLocation.setLatitude(11f);
		geoLocation.setLongitude(48f);
		repository.save(geoLocation);
		final QueryParams params = new QueryParams(Query.misc_geoLocation);

		// when
		final Map<String, Object> result = repository.one(params);

		// then
		assertNotNull(result);
		assertTrue(result.get("_id") instanceof BigInteger);
	}

	@Test
	public void repositoryListener() throws Exception {
		// given
		utils.createContact();
		final Ticket ticket = new Ticket();
		ticket.setSubject("abc");
		ticket.setNote("123");
		ticket.setContactId(BigInteger.ONE);
		ticket.setType(TicketType.REGISTRATION);
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		params.setSearch("ticket.subject='" + ticket.getSubject() + "'");

		// when
		repository.save(ticket);

		// then
		assertTrue(ticket.getNote().contains(Attachment.SEPARATOR));
		assertEquals(1, repository.list(params).size());
	}

	@Test
	public void chat_duplicateNote() throws Exception {
		// given
		final Contact contact = utils.createContact();
		final Chat chat = createChat(contact);
		final Chat chat2 = new Chat();
		chat2.setNote(chat.getNote());
		chat2.setContactId(chat.getContactId());
		chat2.setContactId2(chat.getContactId2());

		// when
		try {
			repository.save(chat2);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalArgumentException ex) {

			// then
			assertEquals("duplicate chat", ex.getMessage());
		}
	}

	@Test
	public void chat_duplicateImage() throws Exception {
		// given
		final byte[] b = new byte[500];
		for (int i = 0; i < b.length; i++)
			b[i] = 23;
		final Contact contact = utils.createContact();
		final Chat chat = new Chat();
		chat.setImage(Attachment.createImage(".jpg", b));
		chat.setContactId(contact.getId());
		chat.setContactId2(adminId);
		final Chat chat2 = new Chat();
		chat2.setImage(Attachment.createImage(".jpg", b));
		chat2.setContactId(chat.getContactId());
		chat2.setContactId2(chat.getContactId2());
		repository.save(chat);

		// when
		try {
			repository.save(chat2);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalArgumentException ex) {

			// then
			assertEquals("duplicate chat", ex.getMessage());
		}
	}

	@Test
	public void attachment_columns() {
		// given
		final Pattern pattern = Pattern.compile(
				".*(image|note|storage|description|aboutMe).*");

		// when
		final boolean matches = pattern.matcher("xyzaboutMeabc").matches();

		// then
		assertTrue(matches);
	}

	@Test
	public void convertObject2Json() throws Exception {
		// given
		final Contact contact = new Contact();
		contact.setAttr("xyz");
		contact.setAboutMe("about others");
		final JsonNode node = new ObjectMapper().convertValue(contact, JsonNode.class);
		((ObjectNode) node).put("attr", "abc");

		// when
		final String result = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);

		// then
		assertTrue(result.contains("abc"));
		assertTrue(result.contains("about others"));
		assertFalse(result.contains("xyz"));
	}
}