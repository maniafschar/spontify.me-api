package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Date;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class AuthenticationServiceTest {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	private ContactToken createToken(final Contact contact) throws Exception {
		final ContactToken token = new ContactToken();
		token.setContactId(contact.getId());
		token.setToken("123456789");
		this.repository.save(token);
		return token;
	}

	@Test
	public void autoLogin() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);
		final ContactToken token = this.createToken(contact);
		final String publicKey = IOUtils.toString(Encryption.class.getResourceAsStream("/keys/publicDB.key"),
				StandardCharsets.UTF_8);

		// when
		final String s = this.authenticationService.autoLogin(publicKey, token.getToken());

		// then
		assertNotNull(s);
	}

	@Test
	public void register() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='test@jq-consulting.de'");
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setClientId(BigInteger.ONE);
		registration.setBirthday(new Date(3000000000L));
		registration.setEmail("test@jq-consulting.de");
		registration.setPseudonym("İrem Fettahoğlu");
		registration.setLanguage("DE");
		registration.setTime(5000);
		registration.setTimezone(TimeZone.getDefault().getID());

		// when
		this.authenticationService.register(registration);

		// then
		assertEquals("rem Fettaholu",
				this.repository.one(Contact.class, (BigInteger) this.repository.one(params).get("contact.id"))
						.getPseudonym());
	}

	@Test
	public void register_blockedEmailDomain() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setBirthday(new Date(3000000000L));
		registration.setEmail("test@0815.ru");
		registration.setPseudonym("qwertz12");
		registration.setLanguage("DE");
		registration.setTime(5000);

		// when
		try {
			this.authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (final IllegalArgumentException ex) {

			// then
			assertEquals("domain", ex.getMessage());
		}
	}

	@Test
	public void register_email() throws Exception {
		// given
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setEmail("testjq-consulting.de");
		registration.setPseudonym("testTEST");
		registration.setLanguage("DE");
		registration.setTime(5000);

		// when
		try {
			this.authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (final IllegalArgumentException ex) {

			// then
			assertEquals("email", ex.getMessage());
		}
	}

	@Test
	public void register_referer() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.ONE);
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setEmail("test_xyz@jq-consulting.de");
		registration.setPseudonym("testTEST");
		registration.setLanguage("DE");
		registration.setClientId(BigInteger.ONE);
		registration.setTimezone("Europe/Berlin");
		registration.setTime(5000);
		registration.setReferer(BigInteger.ONE);
		this.authenticationService.register(registration);
		final List<? extends BaseEntity> list = this.repository.list("from Contact");
		final Contact register = (Contact) list.get(list.size() - 1);
		final QueryParams params = new QueryParams(Query.contact_listFriends);
		params.setId(contact.getId());
		params.setUser(register);

		// when
		this.authenticationService.recoverVerifyEmail(register.getLoginLink(), BigInteger.ONE);

		// then
		assertEquals(ContactLink.Status.Friends, this.repository.one(params).get("contactLink.status"));
	}

	@Test
	public void register_time() throws Exception {
		// given
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setEmail("test@jq-consulting.de");
		registration.setPseudonym("testTEST");
		registration.setLanguage("DE");
		registration.setTime(4999);

		// when
		try {
			this.authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (final IllegalArgumentException ex) {

			// then
			assertEquals("time", ex.getMessage());
		}
	}

	@Test
	public void register_legal() throws Exception {
		// given
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(false);
		registration.setEmail("test@jq-consulting.de");
		registration.setPseudonym("testTEST");
		registration.setLanguage("DE");
		registration.setTime(5000);

		// when
		try {
			this.authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (final IllegalArgumentException ex) {

			// then
			assertEquals("legal", ex.getMessage());
		}
	}

	@Test
	public void verify() throws Exception {
		// given
		final Contact contact = this.utils.createContact(BigInteger.valueOf(4));

		// when
		this.authenticationService.verify(contact.getId(),
				"31c39af069198e168d1d999a983bd93a04cccb06d3ec24acc189ea30968c6cd8", "1645254161315.7888940363091781");

		// then no exception
	}

	@Test
	public void key() throws Exception {
		// given
		final StringBuilder privateKeyBuilder = new StringBuilder();
		final BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						this.getClass().getResourceAsStream("/keys/push_eaa11f91945f6b2997b56f2725be3ee8a11d339c.p8")));
		for (String line; (line = reader.readLine()) != null;) {
			if (line.contains("END PRIVATE KEY"))
				break;
			else if (!line.contains("BEGIN PRIVATE KEY"))
				privateKeyBuilder.append(line);
		}
		final byte[] keyBytes = Base64.getDecoder().decode(privateKeyBuilder.toString().trim());
		final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
		final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		final Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(keyFactory.generatePrivate(keySpec));

		// when
		signature.update("1234567890abcdefghijklmnopqrstxyz".getBytes(StandardCharsets.UTF_8));

		// then no exception
	}

	@Test
	public void referer() {
		// given
		final Pattern pattern = Pattern.compile("(https://([a-z]*.)?skills.community|http[s]?://localhost).*");

		// when
		final boolean result = pattern.matcher("https://sc.skills.community/rest/gähn").find();

		// then
		assertTrue(result);
	}

	@Test
	public void referer2() {
		// given
		final Pattern pattern = Pattern.compile(
				"model.convert: property clientNews|Die Verbindung wurde vom Kommunikationspartner zur|java.io.IOException: Datenübergabe unterbrochen ");

		// when
		final boolean result = pattern.matcher(
				"abc\ndefjava.io.IOException: Die Verbindung wurde vom Kommunikationspartner zurückgesetzt\t\nzwq")
				.find();

		// then
		assertTrue(result);
	}

	@Test
	public void removeFromMap() {
		// given
		final Map<BigInteger, Integer> FAILED_AUTHS = new HashMap<>();
		FAILED_AUTHS.put(BigInteger.ZERO, 6);
		FAILED_AUTHS.put(BigInteger.ONE, 4);
		FAILED_AUTHS.put(BigInteger.TWO, 7);
		FAILED_AUTHS.put(BigInteger.TEN, 3);

		// when
		final Object[] keys = FAILED_AUTHS.keySet().toArray();
		for (final Object k : keys) {
			if (FAILED_AUTHS.get(k) < 5)
				FAILED_AUTHS.remove(k);
		}

		// then no exception
	}

	@Test
	public void deleteAccount() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);

		// when
		this.authenticationService.deleteAccount(this.repository.one(Contact.class, BigInteger.ONE));

		// then no exception
	}

	@Test
	public void encodeToken() {
		// given
		final String s = "CMY1hiWPWOTVuouoyjT6SPrnjrZWeNfuWTSpgRGDwe";

		// when
		long x = 0;
		for (int i = 0; i < s.length(); i++) {
			x += s.charAt(i);
			if (x > 99999999)
				break;
		}
		String s2 = "" + x;
		s2 += s.substring(1, 11 - s2.length());

		// then
		assertEquals("CMY1hiWPWO3907MY1hiWTVuouoyjT6SPrnjrZWeNfuWTSpgRGDwe", s.substring(0, 10) + s2 + s.substring(10));

	}
}