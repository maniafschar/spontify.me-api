package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		SchedulerResult result = surveyService.update();
		assertNull(result.exception);

		// when
		result = surveyService.update();

		// then
		assertNull(result.exception);
		assertTrue(result.result.contains("prediction"), result.result);
	}

	@Test
	public void poll() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		((SurveyServiceMock) surveyService).offset = -2 * 60 * 60;
		final BigInteger clientMarketingId = surveyService.synchronize.poll(BigInteger.ONE, 0);

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

	private String result(final BigInteger clientMarketingId) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(clientMarketing.getStorage()));
		final int max = (int) (10 + Math.random() * 100);
		for (int i = 0; i < max; i++) {
			final ContactMarketing contactMarketing = new ContactMarketing();
			contactMarketing.setClientMarketingId(clientMarketingId);
			contactMarketing.setContactId(BigInteger.ONE);
			contactMarketing.setFinished(Boolean.TRUE);
			contactMarketing.setStorage("{\"q0\":{\"a\":["
					+ (int) (Math.random() * poll.get("questions").get(0).get("answers").size()) + "]}}");
			repository.save(contactMarketing);
		}
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
		repository.save(clientMarketing);
		return surveyService.synchronize.resultAndNotify(clientMarketing.getClientId());
	}
}
