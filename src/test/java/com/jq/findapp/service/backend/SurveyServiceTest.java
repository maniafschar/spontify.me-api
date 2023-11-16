package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.repository.Repository;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class SurveyServiceTest {
	@Autowired
	private SurveyService surveyService;

	@Autowired
	private Repository repository;

	@Test
	public void update_twice() throws Exception {
		// given
		SchedulerResult result = surveyService.update();
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
				new BigInteger(result.result.substring(result.result.lastIndexOf(" ") + 1)));

		// when
		result = surveyService.update();

		// then
		assertNull(result.exception);
		assertEquals("Matchdays already run in last 24 hours", result.result);
		assertNotNull(clientMarketing.getStorage());
	}

	// @Test
	public void manual() {
		// generate survey
		SurveyService.clients.clear();
		SurveyService.clients.put(BigInteger.valueOf(1), 0);
		final Client client = repository.one(Client.class, BigInteger.ONE);
		client.setFbPageAccessToken("");
		client.setFbPageId("");
		repository.save(client);
		surveyService.update();

		// add answers
		final ContactMarketing contactMarketing = new ContactMarketing();
		contactMarketing.setClientMarketingId(BigInteger.ONE);
		contactMarketing.setContactId(BigInteger.ONE);
		contactMarketing.setFinished(Boolean.TRUE);
		contactMarketing.setStorage("{\"q1\":{\"a\":[9],\"t\":\"abc\"}");
		repository.save(contactMarketing);

		// result
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, BigInteger.ONE);
		clientMarketing.setEndDate(new Timestamp(System.currentTimeMilis() - 1000));
		repository.save(clientMarketing);
		surveyService.update();
	}
}
