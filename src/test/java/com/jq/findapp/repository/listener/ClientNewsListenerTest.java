package com.jq.findapp.repository.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ClientNewsListenerTest {
	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void save() throws Exception {
		// given
		final Contact contact = utils.createContact(BigInteger.ONE);
		contact.setSkills("3.12|9.157|9.162");
		contact.setNotification("news");
		repository.save(contact);
		final ClientNews news = new ClientNews();
		news.setClientId(contact.getClientId());
		news.setDescription("abc");
		news.setSkills("9.157");
		news.setPublish(new Timestamp(System.currentTimeMillis()));

		// when
		final boolean saved = repository.save(news);

		// then
		assertTrue(saved);
		final QueryParams params = new QueryParams(Query.contact_listNotification);
		params.setUser(contact);
		params.setSearch("contactNotification.contactId=" + contact.getId());
		Result result;
		while ((result = repository.list(params)).size() == 0)
			Thread.sleep(500);
		assertEquals(1, result.size());
	}
}