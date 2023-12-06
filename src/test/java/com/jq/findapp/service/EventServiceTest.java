package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EventServiceTest {
	@Autowired
	private EventService eventService;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void importEvents() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate='2023-12-02 09:00:00'");

		// when
		final SchedulerResult result = eventService.importEvents();

		// then
		assertNull(result.exception);
		assertEquals("Munich: 56 imported, 0 published", result.result);
		assertEquals(1, repository.list(params).size());
	}

	@Test
	public void importEvents_error() throws Exception {
		// given
		String page = IOUtils.toString(getClass().getResourceAsStream("/html/eventError.html"), StandardCharsets.UTF_8);
		page = page.replace('\n', ' ').replace('\r', ' ').replace('\u0013', ' ');
		page = page.substring(page.indexOf("<ul class=\"m-listing__list\""));
		page = page.substring(0, page.indexOf("</ul>") + 5);
		final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new InputSource(new StringReader(page)));

		// when
		doc.getElementsByTagName("li");

		// then no exception
	}
}