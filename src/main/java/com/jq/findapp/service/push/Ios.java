package com.jq.findapp.service.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.util.Strings;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Ios {
	@Autowired
	private JwtGenerator jwtGenerator;

	@Value("${push.apns.keyId}")
	private String keyId;

	@Value("${push.apns.teamId}")
	private String teamId;

	@Value("${push.apns.url}")
	private String url;

	public void send(Contact contact, String text, String action, int badge) throws Exception {
		final HttpRequest request = HttpRequest.newBuilder()
				.POST(BodyPublishers.ofString(
						IOUtils
								.toString(getClass().getResourceAsStream("/template/push.ios"), StandardCharsets.UTF_8)
								.replace("{text}", text)
								.replace("{badge}", "" + badge)
								.replace("{exec}", Strings.isEmpty(action) ? "" : action)))
				.header("apns-push-type", "alert")
				.header("apns-topic", "com.jq.findapp")
				.header("authorization", "bearer " + jwtGenerator.generateToken(getHeader(), getClaims(), "EC"))
				.header("Content-Type", "application/json")
				.uri(new URI(url + contact.getPushToken()))
				.build();
		final HttpClient client = HttpClient.newBuilder().version(Version.HTTP_2).build();
		final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		if (response.statusCode() >= 300 || response.statusCode() < 200)
			throw new RuntimeException("Failed to push to " + contact.getId() + ": " + text + "\n"
					+ response.statusCode() + "\n" + response.headers());
	}

	private Map<String, String> getHeader() {
		final Map<String, String> map = new HashMap<>(2);
		map.put("alg", "ES256");
		map.put("kid", keyId);
		return map;
	}

	private Map<String, String> getClaims() throws Exception {
		final Map<String, String> map = new HashMap<>(2);
		map.put("iss", teamId);
		map.put("iat", "" + (int) (jwtGenerator.getLastGeneration(keyId) / 1000));
		return map;
	}
}
