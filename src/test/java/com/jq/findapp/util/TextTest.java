package com.jq.findapp.util;

import org.junit.jupiter.api.Test;

import com.jq.findapp.service.NotificationService.NotificationID;

public class TextTest {
	@Test
	public void notificationToId() {
		// given

		// when
		for (NotificationID id : NotificationID.values())
			Text.valueOf("mail_" + id).getText("DE");

		// then no exceptions
	}
}
