package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class }, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class MarketingApiTest {
	@Autowired
	private MarketingApi marketingApi;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void news() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final ClientNews news = new ClientNews();
		news.setClientId(BigInteger.ONE);
		news.setDescription("abc");
		news.setSource("xyz");
		news.setUrl("https://def.gh");
		news.setImage(Attachment.createImage(".jpg",
				IOUtils.toByteArray(this.getClass().getResourceAsStream("/image/party.jpg"))));
		this.repository.save(news);

		// when
		long time1 = System.currentTimeMillis();
		this.marketingApi.news(news.getId());
		time1 = System.currentTimeMillis() - time1;
		long time2 = System.currentTimeMillis();
		final String result = this.marketingApi.news(news.getId());
		time2 = System.currentTimeMillis() - time2;

		// then
		assertTrue(result.contains("<article>abc"));
		assertTrue(
				result.contains("<link rel=\"canonical\" href=\"https://fan-club.online/rest/marketing/news/1\" />"));
		assertTrue((double) time2 / time1 < 0.1, ((double) time2 / time1) + "\ntime1: " + time1 + "\ntime2: " + time2);
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

	@Test
	public void replace_alternate() {
		// given
		final String s = "<htmtl>\n\t<body>\n\t\t<link rel=\"alternate\" href=\"aaa\"/>\n\t</body>\n</html>";

		// when
		final String result = s.replaceFirst("(<link rel=\"alternate\" ([^>].*)>)", "");

		// then
		assertEquals("<htmtl>\n\t<body>\n\t\t\n\t</body>\n</html>", result);
	}

	@Test
	public void locationId() throws Exception {
		// given
		final String id = "1234567890";
		final String s = IOUtils.toString(this.getClass().getResourceAsStream("/json/pollSportsbarResult.json"),
				StandardCharsets.UTF_8).replace("{locationId}", id);
		final Matcher matcher = Pattern.compile("\"locationId\":.?\"(\\d+)\"", Pattern.MULTILINE).matcher(s);

		// when
		matcher.find();
		final String result = matcher.group(1);

		// then
		assertEquals(id, result);
	}
}
