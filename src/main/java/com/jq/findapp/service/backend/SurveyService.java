package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import spinjar.com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.repository.Repository;

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

	@Value("${app.sports.api.token}")
	private String token;

	public SchedulerResult sync() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sync");
		syncMatchdays();
		return result;
	}

	private void syncMatchdays() {
		final BigInteger clientId = BigInteger.valueOf(4);
		final JsonNode matchDays = WebClient
				.create("https://v3.football.api-sports.io/fixtures?team=157&season="
						+ Instant.now().atZone(ZoneId.systemDefault()).getYear())
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(JsonNode.class).block().getBody();
		if (matchDays != null) {
			for (int i = 0; i < matchDays.length; i++) {
				final ClientMarketing clientMarketing = new ClientMarketing();
				clientMarketing.setStartDate(new Timestamp(matchDays[i].fixture.date.getTime() + (2 * 60 * 60 * 1000)));
				clientMarketing
						.setEndDate(new Timestamp(clientMarketing.getStartDate().getTime() + (24 * 60 * 60 * 1000)));
				clientMarketing.setClientId(clientId);
				clientMarketing.setStorage("" + matchDays[i].fixture.id);
			}
		}
	}

	private void syncMatch(final int fixtureId) {
		final BigInteger clientId = BigInteger.valueOf(4);
		final JsonNode matchDay = WebClient
				.create("https://v3.football.api-sports.io/fixtures?id=" + fixtureId)
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(JsonNode.class).block().getBody();
		if (matchDay != null) {

		}
	}
}
