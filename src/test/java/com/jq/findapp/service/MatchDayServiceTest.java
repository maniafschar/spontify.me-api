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
import com.jq.findapp.entity.Storage;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.MatchDayService.PollMatchDay;
import com.jq.findapp.service.model.Response;
import com.jq.findapp.util.Entity;
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
		((MatchDayServiceMock) this.matchDayService).offset = 0;
		final String image = Entity.getImage("https://sc.fan-club.online/images/test.png", 0, 0);
		this.saveStorage("leagues/78", image);
		this.saveStorage("teams/157", image);
		this.saveStorage("teams/165", image);
		this.saveStorage("teams/180", image);
	}

	private void saveStorage(final String label, final String image) {
		final Storage storage = new Storage();
		storage.setLabel("api-sports-/" + label + ".png");
		storage.setStorage(image);
		this.repository.save(storage);
	}

	@Test
	void convert() throws Exception {
		// given

		// when
		final Response response = Json.toObject(IOUtils.toString(
				this.getClass().getResourceAsStream("/json/matchDays.json"), StandardCharsets.UTF_8)
				.replace("{date}", "0")
				.replace("{date1}", "1")
				.replace("{date2}", "2")
				.replace("{dateTBD}", "3"), Response.class);

		// then
		assertNotNull(response);
	}

	@Test
	void convertLastMatch() throws Exception {
		// given

		// when
		final Response response = Json.toObject(IOUtils.toString(
				this.getClass().getResourceAsStream("/json/matchDaysLastMatch.json"), StandardCharsets.UTF_8),
				Response.class);

		// then
		assertNotNull(response);
	}

	@Test
	public void update_twice() throws Exception {
		// given
		this.utils.createContact(BigInteger.valueOf(4));
		CronResult result = this.matchDayService.cron();
		assertNull(result.exception);

		// when
		result = this.matchDayService.cron();

		// then
		assertNull(result.exception);
		assertTrue(result.body.contains("prediction"), result.body);
	}

	@Test
	public void poll() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		((MatchDayServiceMock) this.matchDayService).offset = -9 * 60 * 60;
		final BigInteger clientMarketingId = this.matchDayService.synchronize.playerOfTheMatch(BigInteger.ONE, 0);

		// when
		final String result = this.result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = this.matchDayService.synchronize.prediction(BigInteger.ONE, 0);

		// when
		final String result = this.result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0);
	}

	@Test
	public void prediction_withHistory() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = this.matchDayService.synchronize.prediction(BigInteger.ONE, 0);

		// when
		final String result = this.result(clientMarketingId);

		// then
		assertTrue(Integer.valueOf(result.split(" ")[0]) > 0, result);
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
				this.getClass().getResourceAsStream(
						"/json/matchDays.json"),
				StandardCharsets.UTF_8)
				.replace("\"{date}\"", "0")
				.replace("\"{date1}\"", "1")
				.replace("\"{date2}\"", "2")
				.replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(15)).getEpochSecond())));

		// when
		final Response cache = this.matchDayService.needUpdate(storage,
				"team=786&season=" + this.matchDayService.currentSeason());

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
				this.getClass().getResourceAsStream("/json/matchDays.json"), StandardCharsets.UTF_8)
				.replace("\"{date}\"", "0")
				.replace("\"{date1}\"", "1")
				.replace("\"{date2}\"", "2")
				.replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(14)).getEpochSecond())));

		// when
		final Response cache = this.matchDayService.needUpdate(storage,
				"team=786&season=" + this.matchDayService.currentSeason());

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
				this.getClass().getResourceAsStream(
						"/json/matchDays.json"),
				StandardCharsets.UTF_8)
				.replace("\"{date}\"", "0")
				.replace("\"{date1}\"", "1")
				.replace("\"{date2}\"", "2")
				.replace("\"{dateTBD}\"",
						"" + (long) (Instant.now().plus(Duration.ofDays(14)).getEpochSecond())));

		// when
		final Response cache = this.matchDayService.needUpdate(storage,
				"team=786&season=" + this.matchDayService.currentSeason());

		// then
		assertNotNull(cache);
	}

	@Test
	public void needUpdate_noSeason() throws Exception {
		// given
		final Map<String, Object> storage = new HashMap<>();

		// when
		final Response cache = this.matchDayService.needUpdate(storage, "id=1234");

		// then
		assertNull(cache);
	}

	@Test
	public void retrieveMatchDays() {
		// given
		final Contact contact = new Contact();
		contact.setSkills("9.786");
		contact.setLanguage("DE");

		// when
		final String matches = this.matchDayService.retrieveMatchDays(2, 4, contact);

		// then
		assertEquals(
				"<style>header{font-size:0.7em;padding-top:1em;}match{display:inline-block;float:left;width:100%;font-size:0.7em;}home,away{width:42%;display:inline-block;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;}home{text-align:right;}away{text-align:left;}goals{width:8%;display:inline-block;text-align:center;overflow:hidden;}sep{position:absolute;margin-left:-0.1em;}.highlight{font-weight:bold;}matchdays{text-align:center;display:inline-block;float:left;}</style><matchDays><match skills=\"9.786\"><header>3. Liga · Dietmar-Scholze-Stadion an der Lohmühle · Lübeck · Mi 24.1.2024 19:00</header><home>VfB Lubeck</home><goals>0</goals><sep>:</sep><goals>0</goals><away class=\"highlight\">TSV 1860 München</away></match><match skills=\"9.786\"><header>3. Liga · Städtisches Stadion an der Grünwalder Straße · München · Sa 20.1.2024 16:30</header><home class=\"highlight\">TSV 1860 München</home><goals>0</goals><sep>:</sep><goals>0</goals><away>MSV Duisburg</away></match><match skills=\"9.786\"><header>3. Liga · Carl-Benz-Stadion · Mannheim · Mi 20.12.2023 19:00</header><home>Waldhof Mannheim</home><goals>0</goals><sep>:</sep><goals>0</goals><away class=\"highlight\">TSV 1860 München</away></match><match skills=\"9.786\"><header>3. Liga · SchücoArena · Bielefeld · So 17.12.2023 16:30</header><home>Arminia Bielefeld</home><goals>0</goals><sep>:</sep><goals>0</goals><away class=\"highlight\">TSV 1860 München</away></match><match skills=\"9.786\"><header>3. Liga · Städtisches Stadion an der Grünwalder Straße · München · Sa 9.12.2023 14:00</header><home class=\"highlight\">TSV 1860 München</home><goals>0</goals><sep>:</sep><goals>0</goals><away>Rot-weiss Essen</away></match><match skills=\"9.786\"><header>3. Liga · Stadion Rote Erde · Dortmund · So 3.12.2023 19:30</header><home>Borussia Dortmund II</home><goals>3</goals><sep>:</sep><goals>0</goals><away class=\"highlight\">TSV 1860 München</away></match></matchDays>",
				matches);
	}

	@Test
	public void map() throws Exception {
		// given
		final String json = IOUtils.toString(this.getClass().getResourceAsStream("/json/pollPrediction.json"),
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
		final ClientMarketing clientMarketing = this.repository.one(ClientMarketing.class, clientMarketingId);
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
			this.repository.save(contactMarketing);
		}
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
		this.repository.save(clientMarketing);
		return this.matchDayService.synchronize.result(clientMarketing.getClientId());
	}
}