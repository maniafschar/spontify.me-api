package com.jq.findapp.util;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map.Entry;

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
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.util.Text.TextId;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class TextTest {
	@Autowired
	private Text text;

	@Test
	public void notificationToId() {
		// given
		final Contact contact = new Contact();
		contact.setLanguage("DE");
		contact.setClientId(BigInteger.ONE);

		// when
		for (ContactNotificationTextType id : ContactNotificationTextType.values())
			text.getText(contact, TextId.valueOf("notification_" + id));

		// then no exceptions
	}

	@Test
	public void ids() {
		// given
		final Contact contact = new Contact();
		contact.setLanguage("DE");
		contact.setClientId(BigInteger.ONE);

		// when
		for (TextId id : TextId.values()) {
			System.out.println(id);
			text.getText(contact, id);
		}

		// then no exceptions
	}

	@Test
	public void json() throws Exception {
		// given
		final JsonNode de = new ObjectMapper().readTree(
				IOUtils.toString(Text.class.getResourceAsStream("/lang/DE.json"), StandardCharsets.UTF_8));
		final JsonNode en = new ObjectMapper().readTree(
				IOUtils.toString(Text.class.getResourceAsStream("/lang/EN.json"), StandardCharsets.UTF_8));

		// when
		iterate(de.fields(), en);
		iterate(en.fields(), de);

		// then no exceptions
	}

	private void iterate(Iterator<Entry<String, JsonNode>> iterator, JsonNode compare) {
		while (iterator.hasNext()) {
			final Entry<String, JsonNode> entry = iterator.next();
			if (compare.get(entry.getKey()) == null)
				throw new IllegalArgumentException("missing key " + entry.getKey() + " in en");
			if (entry.getValue().isObject())
				iterate(entry.getValue().fields(), compare.get(entry.getKey()));
		}
	}
}