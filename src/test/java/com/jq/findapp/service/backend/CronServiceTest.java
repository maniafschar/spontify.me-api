package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CronServiceTest {
	@Autowired
	private CronService cronService;

	@Test
	public void cron_minute() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(60).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = cronService.cron("1", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_10minutes() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(1200).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = cronService.cron("*/10", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_10minutes_fail() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(1100).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = cronService.cron("*/10", now);

		// then
		assertFalse(value);
	}

	@Test
	public void cron_4oclock() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(54000).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = cronService.cron("0 15,16,17", now);

		// then
		assertTrue(value);
	}

	@Test
	public void cron_4oclockFail() throws Exception {
		// given
		final ZonedDateTime now = Instant.ofEpochSecond(50400).atZone(ZoneId.of("Europe/Berlin"));

		// when
		final boolean value = cronService.cron("0 16,17", now);

		// then
		assertFalse(value);
	}
}
