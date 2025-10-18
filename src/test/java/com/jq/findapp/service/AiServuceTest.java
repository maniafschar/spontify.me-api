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
	public void ask() {
		// given

		// when
		final List<Location> locations = this.aiService.locations(
				"I'm currenty in Munich, Germany, 80333 and I like Whisky and Beer. Plaese recommend me some locations for tonight.");

		// then
		assertNotNull(locations);
		assertTrue(locations.size() > 0);
	}
}
