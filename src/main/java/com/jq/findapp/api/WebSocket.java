package com.jq.findapp.api;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class WebSocket {
	@Autowired
	private SimpMessagingTemplate messagingTemplate;

	@MessageMapping("/video")
	public void video(Message message) throws Exception {
		System.out.println(message);
		messagingTemplate.convertAndSendToUser("" + message.id2, "/video", message);
	}

	private static class Message {
		String type;
		String name;
		BigInteger id;
		BigInteger id2;
		String candidate;
		String offer;
		String answer;

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

		public BigInteger getId2() {
			return id2;
		}

		public void setId2(BigInteger id2) {
			this.id2 = id2;
		}

		public String getCandidate() {
			return candidate;
		}

		public void setCandidate(String candidate) {
			this.candidate = candidate;
		}

		public String getOffer() {
			return offer;
		}

		public void setOffer(String offer) {
			this.offer = offer;
		}

		public String getAnswer() {
			return answer;
		}

		public void setAnswer(String answer) {
			this.answer = answer;
		}
	}
}