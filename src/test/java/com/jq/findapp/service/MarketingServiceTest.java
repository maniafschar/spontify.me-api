package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Answer;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.CronResult;
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
		poll.textId = TextId.notification_clientMarketingPollPrediction;
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
		final CronResult result = marketingService.runResult();

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
}
