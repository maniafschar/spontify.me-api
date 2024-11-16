package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class RssServiceTest {
	@Autowired
	private RssService rssService;

	@Autowired
	private Utils utils;

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
				IOUtils.toString(RssServiceTest.class.getResourceAsStream("/html/article.html"),
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
	public void imgSrc2() throws Exception {
		// given
		final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		final Matcher matcher = img.matcher(
				IOUtils.toString(RssServiceTest.class.getResourceAsStream("/html/article2.html"),
						StandardCharsets.UTF_8).replace('\n', ' '));

		// when
		final boolean found = matcher.find();

		// then
		assertTrue(found);
		assertEquals(
				"/content/dam/rbb/rbb/rbb24/2023/2023_12/dpa-account/feuerwehr-berlin2.jpg.jpg/size=708x398.jpg",
				matcher.group(1));
	}

	@Test
	public void imgSrc3() throws Exception {
		// given
		final Pattern img2 = Pattern.compile("\\<div.*?\\<picture.*?\\<img .*?src=\\\"(.*?)\\\"");
		final Matcher matcher = img2.matcher(
				IOUtils.toString(RssServiceTest.class.getResourceAsStream("/html/article3.html"),
						StandardCharsets.UTF_8).replace('\n', ' '));

		// when
		final boolean found = matcher.find();

		// then
		assertTrue(found);
		assertEquals(
				"/nachrichten/niedersachsen/lueneburg_heide_unterelbe/treckerdemo616_v-contentgross.jpg",
				matcher.group(1));
	}

	@Test
	public void rss() throws Exception {
		// given
		final ArrayNode rss = (ArrayNode) new XmlMapper()
				.readTree(RssServiceTest.class.getResourceAsStream("/xml/rss.xml"))
				.findValues("item").get(0);

		// when
		final JsonNode node = rss.get(0);

		// then
		assertNotNull(node);
		assertEquals("https://www.rbb24.de/panorama/beitrag/2024/01/schoenefeld-stadt-wachstum-wohnungsbau.html",
				node.get("link").asText());
	}

	@Test
	public void rss2() throws Exception {
		// given
		final ArrayNode rss = (ArrayNode) new XmlMapper()
				.readTree(RssServiceTest.class.getResourceAsStream("/xml/rss2.xml"))
				.findValues("item").get(0);

		// when
		final JsonNode node = rss.get(0);

		// then
		assertNotNull(node);
		assertEquals(
				"https://www.ndr.de/nachrichten/schleswig-holstein/Bauern-wollen-Robert-Habeck-zur-Rede-stellen,habeck1116.html",
				node.get("link").asText());
		assertEquals(
				"2024-01-04T23:29:06.317+01:00",
				node.get("date").asText());
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

	@Test
	public void image() throws Exception {
		// given
		IOUtils.toByteArray(new URL("https://feed.rundschau-online.de/feed/rss/region/index.rss"));
		final String url = "https://fan-club.online/images/icon512.png";

		// when
		final String tag = EntityUtil.getImage(url, EntityUtil.IMAGE_SIZE, 200);

		// then
		assertNotNull(tag);
		assertTrue(tag.startsWith(".jpg" + Attachment.SEPARATOR));
	}

	@Test
	public void image_scale() throws Exception {
		// given

		// when
		final String tag = EntityUtil.getImage(
				"https://www.rbb24.de/content/dam/rbb/rbb/rbb24/2024/2024_01/rbb-reporter/Abendschau-Nebenkostenabrechnung.png.png/size=708x398.png",
				EntityUtil.IMAGE_SIZE, 200);

		// then
		assertNotNull(tag);
		assertTrue(tag.startsWith(".jpg" + Attachment.SEPARATOR));
	}

	@Test
	public void image_webp() throws Exception {
		// given

		// when
		final String tag = EntityUtil.getImage(
				"https://www.muenchen.de/sites/default/files/styles/3_2_w1202/public/2023-07/staatliche_antikensammlungen_und_glyptothek_foto_markus_loex.jpg.webp",
				EntityUtil.IMAGE_SIZE, 200);

		// then
		assertNotNull(tag);
		assertTrue(tag.startsWith(".jpg" + Attachment.SEPARATOR));
	}

	@Test
	public void run() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);

		// when
		final CronResult result = rssService.run();

		// then
		assertTrue(!Strings.isEmpty(result), result.body);
	}
}
