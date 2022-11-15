package com.jq.findapp.service.push;

import java.math.BigInteger;
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

import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.service.NotificationService.Environment;
import com.jq.findapp.util.Strings;

@Component
public class Ios {
	@Autowired
	private JwtGenerator jwtGenerator;

	@Value("${push.apns.keyId}")
	private String keyId;

	@Value("${push.apns.teamId}")
	private String teamId;

	@Value("${push.apns.topic}")
	private String topic;

	@Value("${push.apns.url}")
	private String url;

	@Value("${push.apns.url.test}")
	private String urlTest;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	public Environment send(Contact contact, String text, String action, int badge, String notificationId)
			throws Exception {
		try {
			send(url, contact, text, action, badge, notificationId);
			return Environment.Production;
		} catch (NotFoundException ex) {
			if (adminId.equals(contact.getId())) {
				send(urlTest, contact, text, action, badge, notificationId);
				return Environment.Development;
			}
			throw ex;
		}
	}

	private void send(String url, Contact contact, String text, String action, int badge, String notificationId)
			throws Exception {
		final HttpRequest request = HttpRequest.newBuilder()
				.POST(BodyPublishers.ofString(
						IOUtils
								.toString(getClass().getResourceAsStream("/template/push.ios"), StandardCharsets.UTF_8)
								.replace("{text}", text)
								.replace("{badge}", "" + badge)
								.replace("{notificationId}", notificationId)
								.replace("{exec}", Strings.isEmpty(action) ? "" : action)))
				.header("apns-push-type", "alert")
				.header("apns-topic", topic)
				.header("authorization", "bearer " + jwtGenerator.generateToken(getHeader(), getClaims(), "EC"))
				.header("Content-Type", "application/json")
				.uri(new URI(url + contact.getPushToken()))
				.build();
		final HttpClient client = HttpClient.newBuilder().version(Version.HTTP_2).build();
		final HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
		if (response.statusCode() >= 300 || response.statusCode() < 200)
			throw new NotFoundException(
					"Failed to push to " + contact.getId() + ": " + text + "\n" + response.statusCode() + " "
							+ response.body());
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
		map.put("iat", "" + jwtGenerator.getLastGeneration(keyId, false));
		return map;
	}
}
