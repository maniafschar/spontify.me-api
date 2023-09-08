package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
		System.out.println(matcher.group(1));
		assertEquals(
				"https://i0.wp.com/fcbayerntotal.com/wp-content/uploads/2022/11/Flick_PK-22.05.20.jpg?fit=799%2C597&amp;ssl=1",
				matcher.group(1));
	}
}
