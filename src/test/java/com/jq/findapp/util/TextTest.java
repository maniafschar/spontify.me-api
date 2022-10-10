package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Ticket.TicketType;

public class TextTest {
	@Test
	public void notificationToId() {
		// given

		// when
		for (TicketType id : TicketType.values())
			Text.valueOf("mail_" + id).getText("DE");

		// then no exceptions
	}

	@Test
	public void ids() {
		// given

		// when
		for (Text id : Text.values())
			id.getText("DE");

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
