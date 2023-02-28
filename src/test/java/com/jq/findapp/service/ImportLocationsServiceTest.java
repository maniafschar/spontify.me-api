package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
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
		utils.createContact();
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
	public void importLocationJSON() throws Exception {
		// given
		utils.createContact();
		final String json = IOUtils.toString(getClass().getResourceAsStream("/googleNearByError.json"),
				StandardCharsets.UTF_8);

		// when
		final Location location = importLocationsService.importLocation(new ObjectMapper().readTree(json), "2");

		// then
		assertNotNull(location);
	}
}