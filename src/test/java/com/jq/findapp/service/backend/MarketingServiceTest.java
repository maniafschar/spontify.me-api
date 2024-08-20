package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Answer;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Text.TextId;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class MarketingServiceTest {
	@Autowired
	private Repository repository;

	@Autowired
	private MarketingService marketingService;

	@Autowired
	private Utils utils;

	@Test
	public void html() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final String text = "abc\n\ndef";
		String html = marketingService.createHtmlTemplate(repository.one(Client.class, BigInteger.ONE));

		// when
		html = html.replace("<jq:text />", text);

		// then
		assertTrue(html.contains(text));
		assertTrue(html.indexOf(text) == html.lastIndexOf(text));
	}

	@Test
	public void runResult() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final Question question = new Question();
		question.question = "abc?";
		question.textField = "input";
		final Answer answer = new Answer();
		answer.answer = "a";
		answer.key = "2";
		question.answers.add(answer);
		question.answers.add(answer);
		final Poll poll = new Poll();
		poll.epilog = "epilog";
		poll.prolog = "prolog";
		poll.subject = "subject";
		poll.textId = TextId.marketing_prediction;
		poll.questions.add(question);
		final ClientMarketing clientMarketing = new ClientMarketing();
		clientMarketing.setCreateResult(true);
		clientMarketing.setClientId(BigInteger.ONE);
		clientMarketing.setStartDate(new Timestamp(System.currentTimeMillis() - 1000));
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1));
		clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
		repository.save(clientMarketing);
		final ContactMarketing contactMarketing = new ContactMarketing();
		contactMarketing.setClientMarketingId(clientMarketing.getId());
		contactMarketing.setContactId(BigInteger.ONE);
		contactMarketing.setFinished(true);
		contactMarketing.setStorage("{\"q0\":{\"a\":[1],\"t\":\"25:0\"}}");
		repository.save(contactMarketing);

		// when
		final SchedulerResult result = marketingService.runResult();

		// then
		assertNull(result.exception);
		assertEquals("sent 1 for " + clientMarketing.getId(), result.body.trim());
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
		final Result list = repository.list(params);
		assertEquals(1, list.size());
		assertEquals(
				"{\"participants\":1,\"finished\":1,\"answers\":{\"q0\":{\"a\":[0,1],\"t\":\"<div>25:0</div>\"}}}",
				list.get(0).get("clientMarketingResult.storage"));
		assertEquals(true,
				repository.one(ClientMarketingResult.class, (BigInteger) list.get(0).get("clientMarketingResult.id"))
						.getPublished());
	}

	@Test
	public void locationUpdate() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		Location location = new Location();
		location.setName("abc");
		location.setEmail("abc@jq-consulting.de");
		location.setAddress("Melchiorstr. 9\n81479 München");
		location.setSecret("abc");
		repository.save(location);
		final ClientMarketing clientMarketing = new ClientMarketing();
		clientMarketing.setClientId(BigInteger.ONE);
		clientMarketing.setStartDate(
				new Timestamp(LocalDateTime.now().minus(Duration.ofDays(1)).toInstant(ZoneOffset.UTC).toEpochMilli()));
		clientMarketing.setEndDate(
				new Timestamp(LocalDateTime.now().plus(Duration.ofDays(1)).toInstant(ZoneOffset.UTC).toEpochMilli()));
		clientMarketing.setStorage(
				IOUtils.toString(getClass().getResourceAsStream("/json/pollSportsbar.json"), StandardCharsets.UTF_8));
		repository.save(clientMarketing);
		final ContactMarketing contactMarketing = new ContactMarketing();
		contactMarketing.setClientMarketingId(clientMarketing.getId());
		contactMarketing.setStorage(IOUtils
				.toString(getClass().getResourceAsStream("/json/pollSportsbarResult.json"), StandardCharsets.UTF_8)
				.replace("{locationId}", "" + location.getId())
				.replace("{hash}", "" + location.getSecret().hashCode()));

		// when
		final String result = marketingService.locationUpdate(contactMarketing);
		location = repository.one(Location.class, location.getId());

		// then
		// address is lookedup and "corrected" to test default value
		assertEquals("Melchiorstr. 6\n81479 München", location.getAddress());
		assertEquals("1. Bundesliga, 2. Bundesliga, Football, UEFA Champions League, DFB Pokal",
				location.getDescription());
		assertEquals("Hard Rock Cafe München", location.getName());
		assertEquals("6", location.getNumber());
		assertEquals("2.03|2.04|x.1", location.getSkills());
		assertEquals("hard rock", location.getSkillsText());
		assertEquals("Melchiorstr.", location.getStreet());
		assertEquals("089/2429490", location.getTelephone());
		assertEquals("München", location.getTown());
		assertEquals("https://www.hardrock.com", location.getUrl());
		assertEquals("81479", location.getZipCode());
		assertEquals("<ul><li>Deine Location wurde erfolgreich akualisiert.</li>"
				+ "<li>Marketing-Material senden wir Dir an die Adresse Deiner Location.</li>"
				+ "<li>Wir freuen uns auf eine weitere Zusammenarbeit und melden uns in Bälde bei Dir.</li>"
				+ "<li>Lieben Dank für Dein Feedback.</li></ul>", result);
	}
}