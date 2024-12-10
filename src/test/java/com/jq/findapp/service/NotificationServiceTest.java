package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.service.NotificationService.Environment;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class NotificationServiceTest {
	@Autowired
	private Utils utils;

	@Autowired
	private Ios ios;

	@Autowired
	private Android android;

	public void ios() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);
		contact.setPushToken(
				"a5b9229acefd2351fac694de49d259ab17c2788c112778d36cb555f5a03fc647");
		contact.setClientId(new BigInteger("4"));
		System.setProperty("javax.net.debug", "all");

		// when
		final Environment environmet = this.ios.send(contact.getPseudonym(), contact, "uzgku", "news=823", 12, "1");

		// then
		assertEquals(Environment.Development, environmet);
	}

	@Test
	public void android() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);
		contact.setPushToken(
				"ffYAqQb7RtWNNbECnb7-AU:APA91bFdialtf4-nnmLubNr5lFbg2vSAfnAscCpp_snHzxiYZTzlbuC43gRwtseWr92pbd3ViHU5X4VgCA4INRBJyGiAEcFxFXFIAhOWDQVsO2cC402onC3KmPJrafNcPEZ9J7Rjr_u4");

		// when
		this.android.send(contact.getPseudonym(), contact, "text", "action", "");

		// then no exception
	}
}