package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class RssServiceTest {
	@Test
	public void rssMap() throws Exception {
		// given
		final String url = "https://fcbayerntotal.com/feed/";

		// when
		final JsonNode node = new XmlMapper().readTree(new URL(url)).get("channel").get("item");

		// then
		assertTrue(node instanceof ArrayNode);
		assertTrue(node.get(0).has("title"));
		assertNotNull(((ObjectNode) node.get(0).get("guid")).get("").asText());
	}

	@Test
	public void imgSrc() throws Exception {
		// given
		final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		final Matcher matcher = img.matcher(
				IOUtils.toString(RssServiceTest.class.getResourceAsStream("/article.html"),
						StandardCharsets.UTF_8).replace('\n', ' '));

		// when
		final boolean found = matcher.find();

		// then
		assertTrue(found);
		assertEquals(
				"https://i0.wp.com/fcbayerntotal.com/wp-content/uploads/2022/11/Flick_PK-22.05.20.jpg?fit=799%2C597&amp;ssl=1",
				matcher.group(1));
	}

	@Test
	public void date() throws ParseException {
		// given
		final String date = "Fri, 08 Sep 2023 20:36:08 +0000";
		final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);

		// when
		final Date d = df.parse(date);

		// then
		assertEquals(new Date(1694205368000l), d);
	}
}
