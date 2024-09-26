package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ImportLocationsServiceTest {
	@Autowired
	private ImportLocationsService importLocationsService;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void importLocation() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		importLocationsService.lookup(48, 11);
		BigInteger ticketId = BigInteger.ZERO;
		while (BigInteger.ZERO.equals(ticketId)) {
			Thread.sleep(1000);
			final Result result = repository.list(new QueryParams(Query.misc_listTicket));
			for (int i = 0; i < result.size(); i++) {
				final Map<String, Object> m = result.get(i);
				if (m.get("ticket.type") == TicketType.LOCATION && !"import".equals(m.get("ticket.subject"))
						&& ticketId.longValue() < ((BigInteger) m.get("ticket.id")).longValue())
					ticketId = (BigInteger) m.get("ticket.id");
			}
		}

		// when
		importLocationsService.importLocation(ticketId, "3");

		// then
		final Result result = repository.list(new QueryParams(Query.location_listId));
		final Location location = repository.one(Location.class,
				(BigInteger) result.get(result.size() - 1).get("location.id"));
		assertNotNull(location.getName());
		assertNotNull(location.getAddress());
		assertNotNull(location.getCategory());
		assertTrue(location.getImage().contains(Attachment.SEPARATOR));
		assertTrue(location.getImageList().contains(Attachment.SEPARATOR));
		assertTrue(location.getImage().length() > 5);
		assertTrue(location.getImageList().length() > 5);
	}

	@Test
	public void importLocation_json() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final String json = IOUtils.toString(getClass().getResourceAsStream("/json/googleNearByError.json"),
				StandardCharsets.UTF_8);

		// when
		final Location location = importLocationsService.importLocation(Json.toNode(json), "2");

		// then
		assertNotNull(location);
	}

	@Test
	public void runUrl() throws Exception {
		// given
		final String address = "Wilhelm-Leibl-Straße 22\n81479 München";
		final Storage storage = new Storage();
		storage.setLabel("google-address-" + ("geocode/json?address=" + address.replaceAll("\n", ", ")).hashCode());
		storage.setStorage(
				IOUtils.toString(getClass().getResourceAsStream("/json/googleResponse.json"), StandardCharsets.UTF_8)
						.replace("Melchiorstraße", "Wilhelm-Leibl-Straße")
						.replace("\"6\"", "\"22\""));
		repository.save(storage);
		final Location location = new Location();
		location.setName("Teatro");
		location.setAddress(address);
		repository.save(location);

		// when
		final CronResult result = importLocationsService.runUrl();

		// then
		assertEquals("", repository.one(Location.class, location.getId()).getUrl());
		assertTrue(result.body.contains(" updated"));
		assertNull(result.exception);
	}
}
