package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.jq.findapp.service.AuthenticationService;

@Controller
public class WebSocket {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@MessageMapping("video")
	public void video(VideoMessage message) throws Exception {
		authenticationService.verify(message.getUser(), message.getPassword(), message.getSalt());
		message.setPassword(null);
		message.setSalt(null);
		messagingTemplate.convertAndSendToUser("" + message.getId(), "/video", message);
	}

	@PostMapping("refresh/{id}")
	public void refresh(@RequestBody final Object payload, @PathVariable final BigInteger id) throws Exception {
		messagingTemplate.convertAndSendToUser("" + id, "/refresh", payload);
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

		public void setName(String name) {
			this.name = name;
		}

		public BigInteger getId() {
			return id;
		}

		public void setId(BigInteger id) {
			this.id = id;
		}

		public BigInteger getUser() {
			return user;
		}

		public void setUser(BigInteger user) {
			this.user = user;
		}

		public Map<String, Object> getCandidate() {
			return candidate;
		}

		public void setCandidate(Map<String, Object> candidate) {
			this.candidate = candidate;
		}

		public Map<String, Object> getOffer() {
			return offer;
		}

		public void setOffer(Map<String, Object> offer) {
			this.offer = offer;
		}

		public Map<String, Object> getAnswer() {
			return answer;
		}

		public void setAnswer(Map<String, Object> answer) {
			this.answer = answer;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		public String getSalt() {
			return salt;
		}

		public void setSalt(String salt) {
			this.salt = salt;
		}
	}
}