package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.WebSocket.VideoMessage;

public class WebSocketText {

	@Test
	public void videoMessage() throws Exception {
		// given
		final VideoMessage videoMessage = new VideoMessage();
		videoMessage.id = BigInteger.ONE;
		videoMessage.answer = new HashMap<>();
		videoMessage.answer.put("test", 4);
		videoMessage.answer.put("test2", "abc");
		videoMessage.name = "name";

		// when
		final String s = new ObjectMapper().writeValueAsString(videoMessage);

		// then
		assertNotNull(s);
		assertEquals(
				"{\"id\":1,\"user\":null,\"answer\":{\"test2\":\"abc\",\"test\":4},\"candidate\":null,\"offer\":null,\"name\":\"name\",\"password\":null,\"salt\":null}",
				s);
	}
}
