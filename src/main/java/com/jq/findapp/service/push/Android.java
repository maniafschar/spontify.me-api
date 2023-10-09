package com.jq.findapp.service.push;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.service.NotificationService.Environment;
import com.jq.findapp.util.Strings;

@Component
public class Android {
	@Autowired
	private JwtGenerator jwtGenerator;

	@Value("${push.fcm.keyId}")
	private String keyId;

	@Value("${push.fcm.clientEmail}")
	private String clientEmail;

	@Value("${push.fcm.url}")
	private String url;

	public Environment send(final String from, final Contact contactTo, final String text, final String action,
			final String notificationId)
			throws Exception {
		try {
			send(from, contactTo, text, action, notificationId, false);
		} catch (final Unauthorized ex) {
			send(from, contactTo, text, action, notificationId, true);
		}
		return Environment.Production;
	}

	private void send(final String from, final Contact contactTo, final String text, final String action,
			final String notificationId,
			final boolean reset)
			throws Exception {
		WebClient.create(url)
				.post()
				.contentType(MediaType.APPLICATION_JSON)
				.header("Authorization",
						"Bearer " + jwtGenerator.generateToken(getHeader(), getClaims(reset), "RSA"))
				.bodyValue(
						IOUtils.toString(getClass().getResourceAsStream("/template/push.android"),
								StandardCharsets.UTF_8)
								.replace("{from}", from)
								.replace("{to}", contactTo.getPushToken())
								.replace("{text}", text)
								.replace("{notificationId}", notificationId)
								.replace("{exec}", Strings.isEmpty(action) ? "" : action))
				.retrieve()
				.toEntity(String.class).block().getBody();
	}

	private Map<String, String> getHeader() {
		final Map<String, String> map = new HashMap<>(3);
		map.put("alg", "RS256");
		map.put("typ", "JWT");
		map.put("kid", keyId);
		return map;
	}

	private Map<String, String> getClaims(final boolean reset) throws Exception {
		final long lastGeneration = jwtGenerator.getLastGeneration(keyId, reset);
		final Map<String, String> map = new HashMap<>(5);
		map.put("iss", clientEmail);
		map.put("sub", clientEmail);
		map.put("aud", "https://fcm.googleapis.com/");
		map.put("iat", "" + lastGeneration);
		map.put("exp", "" + (lastGeneration + 3600));
		return map;
	}
}
