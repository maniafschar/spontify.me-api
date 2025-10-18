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
import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.util.Text.TextId;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class TextTest {
	@Autowired
	private Text text;

	@Autowired
	private Utils utils;

	@Test
	public void ids() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);

		// when
		for (final TextId id : TextId.values())
			this.text.getText(contact, id);

		// then no exceptions
	}

	@Test
	public void json() throws Exception {
		// given
		final JsonNode de = Json
				.toNode(IOUtils.toString(Text.class.getResourceAsStream("/lang/DE.json"), StandardCharsets.UTF_8));
		final JsonNode en = Json
				.toNode(IOUtils.toString(Text.class.getResourceAsStream("/lang/EN.json"), StandardCharsets.UTF_8));

		// when
		this.iterate(de.fields(), en);
		this.iterate(en.fields(), de);

		// then no exceptions
	}

	private void iterate(final Iterator<Entry<String, JsonNode>> iterator, final JsonNode compare) {
		while (iterator.hasNext()) {
			final Entry<String, JsonNode> entry = iterator.next();
			if (compare.get(entry.getKey()) == null)
				throw new IllegalArgumentException("missing key " + entry.getKey() + " in en");
			if (entry.getValue().isObject())
				this.iterate(entry.getValue().fields(), compare.get(entry.getKey()));
		}
	}
}