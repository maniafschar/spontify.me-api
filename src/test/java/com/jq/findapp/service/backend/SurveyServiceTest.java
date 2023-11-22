package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.time.LocalDateTime;

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
import com.jq.findapp.entity.Storage;
import com.jq.findapp.repository.Repository;
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

	@Test
	public void update_twice() throws Exception {
		// given
		utils.createContact(BigInteger.valueOf(4));
		SchedulerResult result = surveyService.update();

		// when
		result = surveyService.update();

		// then
		assertNull(result.exception);
		assertEquals("Matchdays already run in last 24 hours", result.result);
	}

	@Test
	public void testPollPlayerOfTheMatch() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final BigInteger clientMarketingId = surveyService.testPoll(false);

		// when
		final String result = surveyService.testResult(clientMarketingId);

		// then
		assertEquals("1", result);
	}

	@Test
	public void testPollPrediction() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final Storage storage = new Storage();
		storage.setLabel("football-0-" + LocalDateTime.now().getYear());
		storage.setStorage(new ObjectMapper()
				.writeValueAsString(surveyService.get("https://v3.football.api-sports.io/fixtures?team=0&")));
		repository.save(storage);
		final BigInteger clientMarketingId = surveyService.testPoll(true);

		// when
		final String result = surveyService.testResult(clientMarketingId);

		// then
		assertEquals("1", result);
	}
}