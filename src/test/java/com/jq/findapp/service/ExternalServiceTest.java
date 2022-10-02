package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Ip;

public class ExternalServiceTest {
	@Test
	public void importLog() throws ParseException {
		// given
		final String line = "217.248.54.4 - - [13/Sep/2022:21:04:54 +0200] \"GET /?ad=google&gclid=EAIaIQobChMIzuS_-rqS-gIVwW3TCh13FQDiEAEYASAAEgIp1PD_BwE HTTP/1.1\" 200 6749 \"https://abc.de\" \"Mozilla/5.0 (iPhone; CPU iPhone OS 15_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6.1 Mobile/15E148 Safari/604.1\"";
		final Pattern pattern = Pattern.compile(
				"([\\d.]+) (\\S) (\\S) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\w+) ([^ ]*) ([^\"]*)\" (\\d+) (\\d+) \"([^\"]*)\" \"([^\"]*)\"");
		final Matcher m = pattern.matcher(line);
		final DateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

		// when
		m.find();

		// then
		assertEquals("217.248.54.4", m.group(1));
		assertEquals("13/Sep/2022:21:04:54 +0200", m.group(4));
		assertEquals("GET", m.group(5));
		assertEquals("/?ad=google&gclid=EAIaIQobChMIzuS_-rqS-gIVwW3TCh13FQDiEAEYASAAEgIp1PD_BwE", m.group(6));
		assertEquals("HTTP/1.1", m.group(7));
		assertEquals("200", m.group(8));
		assertEquals("6749", m.group(9));
		assertEquals("https://abc.de", m.group(10));
		assertEquals(
				"Mozilla/5.0 (iPhone; CPU iPhone OS 15_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.6.1 Mobile/15E148 Safari/604.1",
				m.group(11));
		assertEquals("Tue Sep 13 21:04:54 CEST 2022", "" + dateFormat.parse(m.group(4)));
	}

	@Test
	public void convert() throws Exception {
		// given
		final String response = "{\n"
				+ "	\"ip\": \"188.104.126.43\",\n"
				+ "	\"hostname\": \"dslb-188-104-126-043.188.104.pools.vodafone-ip.de\",\n"
				+ "	\"city\": \"Freilassing\",\n"
				+ "	\"region\": \"Bavaria\",\n"
				+ "	\"country\": \"DE\",\n"
				+ "	\"loc\": \"47.8409,12.9811\",\n"
				+ "	\"org\": \"AS3209 Vodafone GmbH\",\n"
				+ "	\"postal\": \"83395\",\n"
				+ "	\"timezone\": \"Europe/Berlin\"\n"
				+ "}";
		final Matcher loc = Pattern.compile("\"loc\": \"([^\"]*)\"").matcher(response);
		loc.find();

		// when
		final Ip ip = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
				.readValue(response, Ip.class);
		ip.setLatitude(Float.parseFloat(loc.group(1).split(",")[1]));

		// then
		assertEquals("188.104.126.43", ip.getIp());
		assertEquals(12.9811f, ip.getLatitude());
	}
}