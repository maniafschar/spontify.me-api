package com.jq.findapp.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class GeoLocationProcessorTest {
	@Test
	void distance_0() {
		// given

		// when
		final double distance = GeoLocationProcessor.distance(51.11674499511719, 9.528915234234, 51.116745,
				9.528915);

		// then
		assertEquals(0, distance);
	}

	@Test
	void distance_big() {
		// given

		// when
		final double distance = GeoLocationProcessor.distance(51.116745, 9.528915, -51.116745,
				-9.528915);

		// then
		assertEquals(11509.013339978002, distance);
	}
}
