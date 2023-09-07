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
import com.jq.findapp.util.Strings;

public class ExternalServiceTest {
	@Test
	public void importLog() throws ParseException {
		// given
		final String line = "134.96.235.1 - - [22/Oct/2022:06:58:54 +0200] \"GET /js/main.js HTTP/1.1\" 200 4654 \"https://blog.skillvents.com/\" \"\\\"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.18 Safari/537.36\\\"\"";
		final Pattern pattern = Pattern.compile(
				"([\\d.]+) (\\S) (\\S) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\w+) ([^ ]*) ([^\"]*)\" (\\d+) (\\d+) \"([^\"]*)\" \"([^\"]*)\"");
		final Matcher m = pattern.matcher(line.replaceAll("\\\\\"", ""));
		final DateFormat dateFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

		// when
		m.find();

		// then
		assertEquals("134.96.235.1", m.group(1));
		assertEquals("22/Oct/2022:06:58:54 +0200", m.group(4));
		assertEquals("GET", m.group(5));
		assertEquals("/js/main.js", m.group(6));
		assertEquals("HTTP/1.1", m.group(7));
		assertEquals("200", m.group(8));
		assertEquals("4654", m.group(9));
		assertEquals("https://blog.skillvents.com/", m.group(10));
		assertEquals(
				"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.18 Safari/537.36",
				m.group(11));
		assertEquals("Sat Oct 22 06:58:54 CEST 2022", "" + dateFormat.parse(m.group(4)));
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

	@Test
	public void removeSubdomain() {
		// given
		final String url = "https://abc.def.gh";

		// when
		final String result = Strings.removeSubdomain(url);

		// then
		assertEquals("https://def.gh", result);
	}
}