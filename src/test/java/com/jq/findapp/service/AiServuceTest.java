package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Location;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class AiServuceTest {
	@Autowired
	private AiService aiService;

	@Test
	public void locations() {
		// given

		// when
		final List<Location> locations = this.aiService.locations(
				"I'm currenty in Munich, Germany, 80333 and I like Whisky and Beer. Plaese recommend me some locations.");

		// then
		assertNotNull(locations);
		assertTrue(locations.size() > 0);
	}

	@Test
	public void text() {
		// given

		// when
		final String text = this.aiService.text(
				"Tell me some special moments of the matches between FC Bayern München and 1. FC Nürnberg of the past 80 years.");

		// then
		assertNotNull(text);
		assertTrue(text.length() > 20);
	}
}