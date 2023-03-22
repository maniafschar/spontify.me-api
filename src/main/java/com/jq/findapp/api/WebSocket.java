package com.jq.findapp.api;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;


@Controller
public class WebSocket {
	@MessageMapping("/rest")
	public String greeting(String message) throws Exception {
		return "success";
	}
}