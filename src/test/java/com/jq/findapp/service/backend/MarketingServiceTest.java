package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Client;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class MarketingServiceTest {
	@Autowired
	private Repository repository;

	@Autowired
	private MarketingService marketingService;

	@Autowired
	private Utils utils;

	@Test
	public void html() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final String text = "abc\n\ndef";
		String html = marketingService.createHtmlTemplate(repository.one(Client.class, BigInteger.ONE));

		// when
		html = html.replace("<jq:text />", text);

		// then
		assertTrue(html.contains(text));
		assertTrue(html.indexOf(text) == html.lastIndexOf(text));
	}
}