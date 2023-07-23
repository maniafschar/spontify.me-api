package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;

@Controller
public class WebSocket {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private Repository repository;

	private final Pattern numbers = Pattern.compile("\\d+");

	private static final Map<String, String> USERS = new Hashtable<>();

	@MessageMapping("video")
	public void video(final VideoMessage message, @Header(name = "destination") final String destination)
			throws Exception {
		authenticationService.verify(message.getUser(), message.getPassword(), message.getSalt());
		final Log log = new Log();
		final long time = System.currentTimeMillis();
		log.setUri(destination);
		log.setMethod("WS");
		log.setContactId(message.getUser());
		log.setBody(message.answer != null ? "answer:" + message.answer
				: message.candidate != null ? "candidate:" + message.candidate
						: message.offer != null ? "offer:" + message.offer : null);
		if (log.getBody() != null && log.getBody().length() > 255)
			log.setBody(log.getBody().substring(0, 255));
		if (USERS.containsKey("" + message.getId())) {
			message.setPassword(null);
			message.setSalt(null);
			log.setStatus(200);
			messagingTemplate.convertAndSendToUser("" + message.getId(), "/video", message);
		} else {
			final VideoMessage answer = new VideoMessage();
			answer.setAnswer(Collections.singletonMap("userState", "offline"));
			answer.setId(message.getUser());
			log.setStatus(204);
			messagingTemplate.convertAndSendToUser("" + message.getUser(), "/video", answer);
		}
		log.setTime((int) (System.currentTimeMillis() - time));
		log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli() - log.getTime()));
		repository.save(log);
	}

	@PostMapping("refresh/{id}")
	public boolean refresh(@RequestBody final Object payload, @PathVariable final BigInteger id) throws Exception {
		if (USERS.containsKey("" + id)) {
			messagingTemplate.convertAndSendToUser("" + id, "/refresh", payload);
			return true;
		}
		return false;
	}

	@EventListener
	public void handleSessionSubscribeEvent(final SessionSubscribeEvent event) {
		final Matcher matcher = numbers.matcher((String) event.getMessage().getHeaders().get("simpDestination"));
		if (matcher.find())
			USERS.put(matcher.group(), (String) event.getMessage().getHeaders().get("simpSessionId"));
	}

	@EventListener
	public void handleSessionDisconnectEvent(final SessionDisconnectEvent event) {
		USERS.values().remove(event.getMessage().getHeaders().get("simpSessionId"));
	}

	private static class VideoMessage {
		BigInteger id;
		BigInteger user;
		Map<String, Object> answer;
		Map<String, Object> candidate;
		Map<String, Object> offer;
		String name;
		String password;
		String salt;

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}

		public BigInteger getId() {
			return id;
		}

		public void setId(final BigInteger id) {
			this.id = id;
		}

		public BigInteger getUser() {
			return user;
		}

		public void setUser(final BigInteger user) {
			this.user = user;
		}

		public Map<String, Object> getCandidate() {
			return candidate;
		}

		public void setCandidate(final Map<String, Object> candidate) {
			this.candidate = candidate;
		}

		public Map<String, Object> getOffer() {
			return offer;
		}

		public void setOffer(final Map<String, Object> offer) {
			this.offer = offer;
		}

		public Map<String, Object> getAnswer() {
			return answer;
		}

		public void setAnswer(final Map<String, Object> answer) {
			this.answer = answer;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(final String password) {
			this.password = password;
		}

		public String getSalt() {
			return salt;
		}

		public void setSalt(final String salt) {
			this.salt = salt;
		}
	}
}