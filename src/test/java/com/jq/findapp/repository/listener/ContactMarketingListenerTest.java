package com.jq.findapp.repository.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

public class ContactMarketingListenerTest {
	@Test
	public void fillJson() throws Exception {
		// given
		final JsonNode json = new ObjectMapper().readTree("{\"q0\":{\"a\":[],\"t\":\"3:0 für Bayern 💪🔥\"}}");
		json.fieldNames().forEachRemaining(key -> {
			if (json.get(key).has("t"))

				// when
				((ArrayNode) json.get(key).get("a")).add(8);
		});

		// then
		assertEquals(8, json.get("q0").get("a").get(0).asInt());
	}
}
