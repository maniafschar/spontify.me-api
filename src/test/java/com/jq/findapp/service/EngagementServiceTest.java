package com.jq.findapp.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;
import com.jq.findapp.util.Utils;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class, JpaTestConfiguration.class }, properties = { "app.admin.id=3" })
@ActiveProfiles("test")
public class EngagementServiceTest {
	@Autowired
	private EngagementService engagementService;

	@Autowired
	private Utils utils;

	@Test
	public void sendChats() throws Exception {
		// given
		utils.createContact();

		// when
		engagementService.sendChats();

		// then no exception
	}
}
