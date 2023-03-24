package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.jq.findapp.service.AuthenticationService;

@Controller
public class WebSocket {
	@Autowired
	private AuthenticationService authenticationService;
	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/video")
	public void video(Message message) throws Exception {
		authenticationService.verify(message.getUser(), message.getPassword(), message.getSalt());
		message.setPassword(null);
		message.setSalt(null);
		messagingTemplate.convertAndSendToUser("" + message.getId(), "/video", message);
	}

	private static class Message {
		BigInteger id;
		BigInteger user;
		Map<String, Object> candidate;
		Map<String, Object> offer;
		Map<String, Object> answer;
		String name;
		String password;
		String salt;
		String type;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

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