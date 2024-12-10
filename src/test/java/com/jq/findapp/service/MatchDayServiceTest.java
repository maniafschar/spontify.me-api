package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.TestConfig.MatchDayServiceMock;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.MatchDayService.PollMatchDay;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class MatchDayServiceTest {
	@Autowired
	private MatchDayService matchDayService;

	@Autowired
	private Utils utils;

	@Autowired
	private Repository repository;

	@BeforeEach
	public void before() {
		((MatchDayServiceMock) matchDayService).offset = 0;
	}

	@Test
	public void update_twice() throws Exception {
		// given
		utils.createContact(BigInteger.valueOf(4));
		CronResult result = matchDayService.cron();
		assertNull(result.exception);

		// when
		result = matchDayService.cron();

		// then
		assertNull(result.exception);
		assertTrue(result.body.contains("prediction"), result.body);
	}

	@Test
	public void poll() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		((MatchDayServiceMock) matchDayService).offset = -9 * 60 * 60;
		final BigInteger clientMarketingId = matchDayService.synchronize.playerOfTheMatch(BigInteger.ONE, 0);

		// when
		final String result = result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = matchDayService.synchronize.prediction(BigInteger.ONE, 0);

		// when
		final String result = result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction_withHistory() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = matchDayService.synchronize.prediction(BigInteger.ONE, 0);

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
		final JsonNode json = Json.toNode(
				"{\"get\":\"fixtures\",\"parameters\":{\"id\":\"209214\"},\"errors\":{\"rateLimit\":\"Too many requests. Your rate limit is 10 requests per minute.\"},\"results\":0,\"paging\":{\"current\":1,\"total\":1},\"response\":[]}");

		// when
		final boolean result = json.has("errors") && json.get("errors").size() > 0;

		// then
		assertTrue(result);
	}

	@Test
	public void needUpdate_15DaysAhead() throws Exception {
		// given
		final Map<String, Object> storage = new HashMap<>();
		storage.put("storage.createdAt", new Timestamp(Instant.now().toEpochMilli()));
		storage.put("storage.storage", IOUtils.toString(
				getClass().getResourceAsStream(
						"/json/matchDays.json"),
				StandardCharsets.UTF_8).replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(15)).getEpochSecond())));

		// when
		final JsonNode cache = matchDayService.needUpdate(storage,
				"team=786&season=" + matchDayService.currentSeason());

		// then
		assertNotNull(cache);
	}

	@Test
	public void needUpdate_14DaysAhead() throws Exception {
		// given
		final Map<String, Object> storage = new HashMap<>();
		storage.put("storage.createdAt", new Timestamp(Instant.now().minus(Duration.ofHours(26)).toEpochMilli()));
		storage.put("storage.modifiedAt", new Timestamp(Instant.now().minus(Duration.ofHours(25)).toEpochMilli()));
		storage.put("storage.storage", IOUtils.toString(
				getClass().getResourceAsStream(
						"/json/matchDays.json"),
				StandardCharsets.UTF_8).replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(14)).getEpochSecond())));

		// when
		final JsonNode cache = matchDayService.needUpdate(storage,
				"team=786&season=" + matchDayService.currentSeason());

		// then
		assertNull(cache);
	}

	@Test
	public void needUpdate_justModified() throws Exception {
		// given
		final Map<String, Object> storage = new HashMap<>();
		storage.put("storage.createdAt", new Timestamp(Instant.now().minus(Duration.ofHours(2)).toEpochMilli()));
		storage.put("storage.modifiedAt", new Timestamp(Instant.now().minus(Duration.ofHours(1)).toEpochMilli()));
		storage.put("storage.storage", IOUtils.toString(
				getClass().getResourceAsStream(
						"/json/matchDays.json"),
				StandardCharsets.UTF_8).replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(14)).getEpochSecond())));

		// when
		final JsonNode cache = matchDayService.needUpdate(storage,
				"team=786&season=" + matchDayService.currentSeason());

		// then
		assertNotNull(cache);
	}

	@Test
	public void needUpdate_noSeason() throws Exception {
		// given
		final Map<String, Object> storage = new HashMap<>();

		// when
		final JsonNode cache = matchDayService.needUpdate(storage, "id=1234");

		// then
		assertNull(cache);
	}

	@Test
	public void retrieveMatchDays() {
		// given
		final Contact contact = new Contact();
		contact.setSkills("9.157");
		contact.setLanguage("DE");

		// when
		final String matches = matchDayService.retrieveMatchDays(2, 4, contact);

		// then
		assertEquals(
				"<style>header{font-size:0.7em;padding-top:1em;}match{display:inline-block;float:left;width:100%;font-size:0.7em;}home,away{width:42%;display:inline-block;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;}home{text-align:right;}away{text-align:left;}goals{width:8%;display:inline-block;text-align:center;overflow:hidden;}sep{position:absolute;margin-left:-0.1em;}.highlight{font-weight:bold;}matchdays{text-align:center;display:inline-block;float:left;}</style><matchDays><match skills=\"9.157\"><header>Bundesliga · PreZero Arena · Sinsheim · Sa 18.5.2024 15:30</header><home>1899 Hoffenheim</home><goals>&nbsp;</goals><sep>:</sep><goals>&nbsp;</goals><away class=\"highlight\">Bayern München</away></match><match skills=\"9.157\"><header>UEFA Champions League · Old Trafford · Manchester · Di 12.12.2023 21:00</header><home>Manchester United</home><goals>&nbsp;</goals><sep>:</sep><goals>&nbsp;</goals><away class=\"highlight\">Bayern München</away></match><match skills=\"9.157\"><header>UEFA Champions League · Allianz Arena · München · Mi 29.11.2023 21:00</header><home class=\"highlight\">Bayern München</home><goals>&nbsp;</goals><sep>:</sep><goals>&nbsp;</goals><away>FC Copenhagen</away></match><match skills=\"9.157\"><header>UEFA Champions League · Allianz Arena · München · Mi 8.11.2023 21:00</header><home class=\"highlight\">Bayern München</home><goals>&nbsp;</goals><sep>:</sep><goals>&nbsp;</goals><away>Galatasaray</away></match><match skills=\"9.157\"><header>Bundesliga · SIGNAL IDUNA PARK · Dortmund · Sa 4.11.2023 18:30</header><home>Borussia Dortmund</home><goals>0</goals><sep>:</sep><goals>4</goals><away class=\"highlight\">Bayern München</away></match><match skills=\"9.157\"><header>DFB Pokal · Stadion Ludwigspark · Saarbrücken · Mi 1.11.2023 20:45</header><home>FC Saarbrücken</home><goals>2</goals><sep>:</sep><goals>1</goals><away class=\"highlight\">Bayern München</away></match></matchDays>",
				matches);
	}

	@Test
	public void map() throws Exception {
		// given
		final String json = IOUtils.toString(getClass().getResourceAsStream("/json/pollPrediction.json"),
				StandardCharsets.UTF_8);

		// when
		final PollMatchDay result = Json.toObject(json, PollMatchDay.class);

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
				"Lieben Dank für die Teilnahme!\nDas Ergebnis wird kurz vor dem Spiel hier bekanntgegeben.",
				result.epilog);
	}

	private String result(final BigInteger clientMarketingId) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		final PollMatchDay poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()),
				PollMatchDay.class);
		final int max = (int) (10 + Math.random() * 100);
		for (int i = 0; i < max; i++) {
			final ContactMarketing contactMarketing = new ContactMarketing();
			contactMarketing.setClientMarketingId(clientMarketingId);
			contactMarketing.setContactId(BigInteger.ONE);
			contactMarketing.setFinished(Boolean.TRUE);
			contactMarketing.setStorage("{\"q0\":{\"a\":["
					+ (int) (Math.random() * poll.questions.get(0).answers.size()) + "]}}");
			repository.save(contactMarketing);
		}
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
		repository.save(clientMarketing);
		return matchDayService.synchronize.result(clientMarketing.getClientId());
	}
}
