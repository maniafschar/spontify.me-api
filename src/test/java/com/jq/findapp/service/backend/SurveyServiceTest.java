package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.TestConfig.SurveyServiceMock;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.backend.SurveyService.PollSurvey;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class SurveyServiceTest {
	@Autowired
	private SurveyService surveyService;

	@Autowired
	private Utils utils;

	@Autowired
	private Repository repository;

	@BeforeEach
	public void before() {
		((SurveyServiceMock) surveyService).offset = 0;
	}

	@Test
	public void update_twice() throws Exception {
		// given
		utils.createContact(BigInteger.valueOf(4));
		SchedulerResult result = surveyService.run();
		assertNull(result.exception);

		// when
		result = surveyService.run();

		// then
		assertNull(result.exception);
		assertTrue(result.result.contains("prediction"), result.result);
	}

	@Test
	public void poll() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		((SurveyServiceMock) surveyService).offset = -2 * 60 * 60;
		final BigInteger clientMarketingId = surveyService.synchronize.playerOfTheMatch(BigInteger.ONE, 0);

		// when
		final String result = result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		((SurveyServiceMock) surveyService).offset = -1;
		final BigInteger clientMarketingId = surveyService.synchronize.prediction(BigInteger.ONE, 0);

		// when
		final String result = result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction_withHistory() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = surveyService.synchronize.prediction(BigInteger.ONE, 0);

		// when
		final String result = result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void regex() {
		// given
		final Pattern pattern = Pattern.compile("(\\d+)[ ]*[ :-]+[ ]*(\\d+)", Pattern.MULTILINE);
		final Matcher matcher = pattern.matcher("abc\nxyz1 -  8op\nxyz");

		// when
		matcher.find();

		// then
		assertEquals("1", matcher.group(1));
		assertEquals("8", matcher.group(2));
	}

	@Test
	public void errors() throws Exception {
		// given
		final JsonNode json = new ObjectMapper().readTree(
				"{\"get\":\"fixtures\",\"parameters\":{\"id\":\"209214\"},\"errors\":{\"rateLimit\":\"Too many requests. Your rate limit is 10 requests per minute.\"},\"results\":0,\"paging\":{\"current\":1,\"total\":1},\"response\":[]}");

		// when
		final boolean result = json.has("errors") && json.get("errors").size() > 0;

		// then
		assertTrue(result);
	}

	@Test
	public void map() throws Exception {
		// given
		final String json = IOUtils.toString(getClass().getResourceAsStream("/json/pollPrediction.json"),
				StandardCharsets.UTF_8);

		// when
		final PollSurvey result = new ObjectMapper().readValue(json, PollSurvey.class);

		// then
		assertEquals("Prediction", result.type);
		assertEquals("https://media.api-sports.io/football/teams/167.png", result.home);
		assertEquals("https://media.api-sports.io/football/teams/157.png", result.away);
		assertEquals("1899 Hoffenheim", result.homeName);
		assertEquals("Bayern München", result.awayName);
		assertEquals(167, result.homeId);
		assertEquals(157, result.awayId);
		assertEquals("https://media.api-sports.io/football/leagues/78.png", result.league);
		assertEquals("Bundesliga", result.leagueName);
		assertEquals(1716039000, result.timestamp);
		assertEquals("PreZero Arena", result.venue);
		assertEquals("Sinsheim", result.city);
		assertEquals("away", result.location);
		assertEquals(2, result.statistics.get(0).get("home"));
		assertEquals(8, result.statistics.get(0).get("away"));
		assertEquals("Shots on Goal", result.statistics.get(0).get("label"));
		assertEquals("1 : 3|18.1.2019 20:30", result.matches.get(3));
		assertEquals("Erzielen wir eines der letzten Ergebnisse?", result.questions.get(0).question);
		assertEquals(8, result.questions.get(0).answers.size());
		assertEquals("1 : 0", result.questions.get(0).answers.get(1).answer);
		assertEquals("textarea", result.questions.get(0).textField);
		assertEquals(
				"<b>Ergebnistipps</b> zum Bundesliga Spiel<div style=\"padding:1em 0;font-weight:bold;\">1899 Hoffenheim - Bayern München</div>vom <b>18.5.2024 um 15:30 Uhr</b>. Möchtest Du teilnehmen?",
				result.prolog);
		assertEquals(
				"Lieben Dank für die Teilnahme!\nDas Ergebnis wird kurz vor dem Spiel hier bekanntgegeben.\n\nLust auf mehr <b>Fan Feeling</b>? In unserer neuen App bauen wir eine neue <b>Fußball Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.",
				result.epilog);
	}

	private String result(final BigInteger clientMarketingId) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		final PollSurvey poll = new ObjectMapper().readValue(Attachment.resolve(clientMarketing.getStorage()),
				PollSurvey.class);
		final int max = (int) (10 + Math.random() * 100);
		for (int i = 0; i < max; i++) {
			final ContactMarketing contactMarketing = new ContactMarketing();
			contactMarketing.setClientMarketingId(clientMarketingId);
			contactMarketing.setContactId(BigInteger.ONE);
			contactMarketing.setFinished(Boolean.TRUE);
			contactMarketing.setStorage("{\"answers\":{\"q0\":{\"a\":["
					+ (int) (Math.random() * poll.questions.get(0).answers.size()) + "]}}}");
			repository.save(contactMarketing);
		}
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
		repository.save(clientMarketing);
		return surveyService.synchronize.result(clientMarketing.getClientId());
	}
}
