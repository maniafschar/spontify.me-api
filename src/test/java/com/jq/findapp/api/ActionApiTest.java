package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

public class ActionApiTest {
	@Test
	public void google() throws Exception {
		// given
		final String response = IOUtils.toString(getClass().getResourceAsStream("/googleResponse.json"),
				StandardCharsets.UTF_8);

		// when
		final JsonNode data = new ObjectMapper().readTree(response);

		// then
		assertEquals("OK", data.get("status").asText());
		assertEquals(9, data.get("results").get(0).get("address_components").size());
		assertEquals(3, data.get("results").get(0).get("address_components").get(0).size());
		assertEquals("route",
				data.get("results").get(0).get("address_components").get(1).get("types").get(0).asText());
	}
}
