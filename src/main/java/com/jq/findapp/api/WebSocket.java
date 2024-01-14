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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.ChatService;

@RestController
@RequestMapping("ws")
public class WebSocket {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@Autowired
	private ChatService chatService;

	@Autowired
	private Repository repository;

	private final Pattern numbers = Pattern.compile("\\d+");

	private final Map<BigInteger, String> USERS = new Hashtable<>();

	@MessageMapping("video")
	public void video(final VideoMessage message, @Header(name = "destination") final String destination)
			throws Exception {
		final Contact contact = authenticationService.verify(message.getUser(), message.getPassword(),
				message.getSalt());
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
		if (message.answer != null || chatService.isVideoCallAllowed(contact, message.getId())) {
			if (USERS.containsKey(message.getId())) {
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
		} else
			log.setStatus(500);
		log.setTime((int) (System.currentTimeMillis() - time));
		log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli() - log.getTime()));
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setSearch("log.createdAt=cast('" + log.getCreatedAt()
				+ "' as timestamp) and log.body like '"
				+ (log.getBody() == null ? "" : log.getBody().replace('\n', '%').replace(';', '_'))
				+ "' and log.uri='" + log.getUri()
				+ "' and log.method='WS'");
		final Result list = repository.list(params);
		if (list.size() > 0) {
			final Log logOld = repository.one(Log.class, (BigInteger) list.get(0).get("log.id"));
			if (logOld.getWebCall() == null)
				logOld.setWebCall("2");
			else
				logOld.setWebCall("" + (Integer.parseInt(logOld.getWebCall()) + 1));
			repository.save(logOld);
		} else
			repository.save(log);
	}

	@PostMapping("refresh/{id}")
	public boolean refresh(@RequestBody final Object payload, @PathVariable final BigInteger id) throws Exception {
		if (USERS.containsKey(id)) {
			messagingTemplate.convertAndSendToUser("" + id, "/refresh", payload);
			return true;
		}
		return false;
	}

	@EventListener
	public void handleSessionSubscribeEvent(final SessionSubscribeEvent event) {
		final Matcher matcher = numbers.matcher((String) event.getMessage().getHeaders().get("simpDestination"));
		if (matcher.find())
			USERS.put(new BigInteger(matcher.group()), (String) event.getMessage().getHeaders().get("simpSessionId"));
	}

	@EventListener
	public void handleSessionDisconnectEvent(final SessionDisconnectEvent event) {
		USERS.values().remove(event.getMessage().getHeaders().get("simpSessionId"));
	}

	@GetMapping("active/{user}")
	public boolean active(@PathVariable final BigInteger user) {
		return USERS.containsKey(user);
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