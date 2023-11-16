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
import com.jq.findapp.entity.ClientMarketing;
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

	@Test
	public void publish() {
		// given
		SurveyService.clients.put(BigInteger.valueOf(4), 0);
		final String fbPageId = "xxx";
		final Map<String, String> body = new HashMap<>();
		body.put("message", "Umfrage Spieler des Spiels");
		body.put("image", "https://fcbayerntotal.fan-club.online/images/logo.png");
		body.put("link", "https://fcbayerntotal.fan-club.online?m=4");
		body.put("access_token", "yyy");

		// when
		final String response = WebClient.create("https://graph.facebook.com/v18.0/" + fbPageId + "/feed")
				.post().bodyValue(body).retrieve()
				.toEntity(String.class).block().getBody();

		// then: check on FB
		assertNotNull(response);
		assertTrue(response.contains("\"id\":"));
	}
}
