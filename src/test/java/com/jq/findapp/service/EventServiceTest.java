package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class EventServiceTest {
	@Autowired
	private EventService eventService;

	@Autowired
	private Utils utils;

	@Test
	public void importEvents() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);

		// when
		final SchedulerResult result = eventService.importEvents();

		// then
		assertNull(result.exception);
		assertEquals("Munich: 94", result.result);
	}
}