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

	// @Test
	public void ios() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);
		contact.setPushToken(
				"80d52c0437fe03854d040aeb0eb19bdb705bf56f92ec405ffb2c85b536572586a94eebf7c65087b9c75aff9dc4f2c722d0bac1e48853e52aadc3936ad78875bfd5e37fb65756b3fd038794f467e9ccb2");
		contact.setClientId(new BigInteger("4"));
		System.setProperty("javax.net.debug", "all");

		// when
		final Environment environmet = this.ios.send(contact.getPseudonym(), contact, "test", "news=823", 12, "1");

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