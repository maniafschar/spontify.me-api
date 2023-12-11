package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.service.ExternalService;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ActionApiTest {
	@Autowired
	private ExternalService externalService;

	@Test
	public void google() throws Exception {
		// given
		final String response = IOUtils.toString(getClass().getResourceAsStream("/json/googleResponse.json"),
				StandardCharsets.UTF_8);

		// when
		final JsonNode data = new ObjectMapper().readTree(response);
		final GeoLocation address = externalService.convertAddress(data).get((0));

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

	@Test
	public void paypal_refund() throws Exception {
		// given
		final JsonNode n = new ObjectMapper()
				.readTree(IOUtils.toString(getClass().getResourceAsStream("/json/paypalWebhook.json"),
						StandardCharsets.UTF_8));

		// when
		final String href = n.get("resource").get("links").get(1).get("href").asText();

		// then
		assertEquals("https://api.sandbox.paypal.com/v2/payments/captures/2J476521SE713422J", href);
	}

	@Test
	public void replaceFirst() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("<meta property=\"og:url\" content=\"([^\"].*)\"",
				"<meta property=\"og:url\" content=\"xxx\"");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t<meta property=\"og:url\" content=\"xxx\"/>\n\t</body>\n</html>", result);
	}
}
