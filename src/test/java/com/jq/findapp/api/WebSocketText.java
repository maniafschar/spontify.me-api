package com.jq.findapp.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigInteger;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.jq.findapp.api.WebSocket.VideoMessage;
import com.jq.findapp.util.Json;

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
		final String s = Json.toString(videoMessage);

		// then
		assertNotNull(s);
		assertEquals(
				"{\"id\":1,\"user\":null,\"answer\":{\"test2\":\"abc\",\"test\":4},\"candidate\":null,\"offer\":null,\"name\":\"name\",\"password\":null,\"salt\":null}",
				s);
	}
}
