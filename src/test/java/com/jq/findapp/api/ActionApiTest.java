package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.service.ExternalService;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class, JpaTestConfiguration.class })
@ActiveProfiles("test")
public class ActionApiTest {
	@Autowired
	private ExternalService externalService;

	@Test
	public void google() throws Exception {
		// given
		final String response = IOUtils.toString(getClass().getResourceAsStream("/googleResponse.json"),
				StandardCharsets.UTF_8);

		// when
		final JsonNode data = new ObjectMapper().readTree(response);
		final GeoLocation address = externalService.convertGoogleAddress(data);

		// then
		assertEquals("OK", data.get("status").asText());
		assertEquals(9, data.get("results").get(0).get("address_components").size());
		assertEquals(3, data.get("results").get(0).get("address_components").get(0).size());
		assertEquals("route",
				data.get("results").get(0).get("address_components").get(1).get("types").get(0).asText());
		assertEquals("DE", address.getCountry());
		assertEquals("6", address.getNumber());
		assertEquals("Melchiorstraße", address.getStreet());
		assertEquals("München", address.getTown());
		assertEquals("81479", address.getZipCode());
		assertEquals((float) 48.072197, address.getLatitude());
	}
}
