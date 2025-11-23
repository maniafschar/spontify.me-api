package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.Cron;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class CronServiceTest {
	@Autowired
	private CronService cronService;

	@Autowired
	private ChatService chatService;

	@Autowired
	private Repository repository;

	@Test
	public void cron_minute() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(60).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("1", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_10minutes() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(1200).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("*/10", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_10minutes_fail() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(1100).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("*/10", now);

		// then
		assertFalse(value);
	}

	@Test
	public void cron_4oclock() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(54000).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("0 15,16,17", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_11_50() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(1763895030).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("50 11", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_4oclockFail() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(50400).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = this.cronService.cron("0 16,17", now);

		// then
		assertFalse(value);
	}

	@Test
	public void methods() throws Exception {
		// given

		// when
		final Method method = this.chatService.getClass().getMethod("cron");

		// then
		assertTrue(method.isAnnotationPresent(Cron.class));
	}

	@Test
	public void run_fourTimes() throws Exception {
		// given
		final String time = Instant.now().atZone(ZoneId.of("UCT")).toString().substring(0, 19);
		this.cronService.run();
		this.cronService.run();
		this.cronService.run();

		// when
		this.cronService.run();

		// then
		List<?> list = null;
		for (int i = 0; i < 100; i++) {
			Thread.sleep(1000);
			list = this.repository
					.list("from Log where uri like '/support/cron/EventService/cron' and createdAt>=cast('"
							+ time + "' as timestamp)");
			if (list.size() > 3)
				break;
		}
		assertEquals(4, list == null ? 0 : list.size());
	}
}
