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
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

	@Value("${app.sports.api.token}")
	private String token;

	public SchedulerResult sync() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sync");
		if (LocalDateTime.now().getHour() == 0 && LocalDateTime.now().getMinute() < 10)
			syncMatchdays();
		return result;
	}

	private void syncMatchdays() {
		final BigInteger clientId = BigInteger.valueOf(4);
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId + 
				 " and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		if (repository.list(params).size() == 0) {
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
					if (matchDays[i].fixture.date.getTime() > System.currenTimeMillis()) {
						final ClientMarketing clientMarketing = new ClientMarketing();
						clientMarketing.setStartDate(new Timestamp(matchDays[i].fixture.date.getTime() + (2 * 60 * 60 * 1000)));
						clientMarketing.setEndDate(new Timestamp(clientMarketing.getStartDate().getTime() + (24 * 60 * 60 * 1000)));
						clientMarketing.setClientId(clientId);
						clientMarketing.setStorage("" + matchDays[i].fixture.id);
						repository.save(clientMarketing);
					}
				}
			}
		}
	}

	private void syncMatch() {
		final BigInteger clientId = BigInteger.valueOf(4);
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate<='" + Instant.now() + "' and clientMarketing.endDate>'" + Instant.now() +
				"' and clientMarketing.clientId=" + clientId + 
				" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		final Result list = repository.list(params);
		if (list.size() > 0) {
			final JsonNode matchDay = WebClient
					.create("https://v3.football.api-sports.io/fixtures?id=" + list.get(0).get("clientMarketing.storage"))
					.get()
					.header("x-rapidapi-key", token)
					.header("x-rapidapi-host", "v3.football.api-sports.io")
					.retrieve()
					.toEntity(JsonNode.class).block().getBody();
			if (matchDay != null) {
				JsonNode e = matchDay.findPath("players");
				e = e.get(e.get(0).get("team").get("id").asInt() == 157 ? 0 : 1).get("players");
				for (int i = 0; i < e.size(); i++) {
					e.get(i).get("player").get("name").asText() +
						"<desc>" + e.get(i).get("statistics").get(0).get("games").get("minutes").asText() + "</desc>";
				}
            + matchDay.get("players")
                .get(i)
                .get("players")
                .get(i2)
                .get("statistics")
                .get(0)
                .get("games")
                .get("rating")
                .asText()
            + " " + matchDay.get("players")
                .get(i)
                .get("players")
                .get(i2)
                .get("statistics")
                .get(0)
                .get("games")
                .get("minutes")
                .asText());

			}
		}
	}
}
