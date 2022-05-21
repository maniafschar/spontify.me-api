package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Date;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.JpaTestConfiguration;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.EncryptionTest;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class, JpaTestConfiguration.class })
@ActiveProfiles("test")
public class AuthenticationTest {
	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private Repository repository;

	@Autowired
	private Ios ios;

	@Autowired
	private Android android;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private Contact createContact() throws Exception {
		Contact contact = repository.one(Contact.class, adminId);
		if (contact == null) {
			int i = 1;
			do {
				contact = new Contact();
				contact.setEmail("test" + i + "@jq-consulting.de");
				contact.setLanguage("DE");
				contact.setIdDisplay("123456" + i++);
				contact.setFacebookId("1234567890");
				contact.setBirthday(new Date(3000000000L));
				contact.setGender((short) 1);
				contact.setPseudonym("pseudonym");
				contact.setVerified(true);
				contact.setPassword(Encryption.encryptDB("secret_password"));
				contact.setPasswordReset(System.currentTimeMillis());
				repository.save(contact);
			} while (contact.getId().intValue() < 3);
		} else {
			contact.setPassword(Encryption.encryptDB("secret_password"));
			repository.save(contact);
		}
		return contact;
	}

	private ContactToken createToken(final Contact contact) throws Exception {
		final ContactToken token = new ContactToken();
		token.setContactId(contact.getId());
		token.setToken("123456789");
		repository.save(token);
		return token;
	}

	@Test
	public void getAutoLogin() throws Exception {
		// given
		final Contact contact = createContact();
		final ContactToken token = createToken(contact);
		final String t = EncryptionTest.encryptBrowser(token.getToken());
		final String publicKey = IOUtils.toString(Encryption.class.getResourceAsStream("/keys/publicDB.key"),
				StandardCharsets.UTF_8);
		// when
		final String s = authenticationService.getAutoLogin(publicKey, t);

		// then
		assertNotNull(s);
	}

	@Test
	public void register() throws Exception {
		// given
		createContact();
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='test@jq-consulting.de'");
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setBirthday(new Date(3000000000L));
		registration.setEmail("test@jq-consulting.de");
		registration.setPseudonym("İrem Fettahoğlu");
		registration.setLanguage("DE");
		registration.setTime(5000);

		// when
		authenticationService.register(registration);

		// then
		assertEquals("rem Fettaholu",
				repository.one(Contact.class, (BigInteger) repository.one(params).get("contact.id")).getPseudonym());
	}

	@Test
	public void register_blockedEmailDomain() throws Exception {
		// given
		createContact();
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setBirthday(new Date(3000000000L));
		registration.setEmail("test@0815.ru");
		registration.setPseudonym("qwertz12");
		registration.setLanguage("DE");
		registration.setTime(5000);

		// when
		try {
			authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalAccessException ex) {

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
			authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalAccessException ex) {

			// then
			assertEquals("email", ex.getMessage());
		}
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
			authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalAccessException ex) {

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
			authenticationService.register(registration);
			throw new RuntimeException("no exception thrown");
		} catch (IllegalAccessException ex) {

			// then
			assertEquals("legal", ex.getMessage());
		}
	}

	@Test
	public void verify() throws Exception {
		// given
		final Contact contact = createContact();
		final Field f = authenticationService.getClass().getDeclaredField("TIMEOUT");
		f.setAccessible(true);
		f.setLong(null, System.currentTimeMillis());

		// when
		authenticationService.verify(contact.getId(),
				"f877829076e5d8c1b148e547c60058706f113264070119e7423a0bcb0a66866c", "1645254161315.7888940363091781");

		// then no exception
	}

	@Test
	public void key() throws Exception {
		final StringBuilder privateKeyBuilder = new StringBuilder();
		final BufferedReader reader = new BufferedReader(
				new InputStreamReader(
						getClass().getResourceAsStream("/keys/push_eaa11f91945f6b2997b56f2725be3ee8a11d339c.p8")));
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
		signature.update("1234567890abcdefghijklmnopqrstxyz".getBytes(StandardCharsets.UTF_8));
	}

	public void ios() throws Exception {
		// given
		final Contact contact = createContact();
		contact.setPushToken("e8206cb2d430ff4c84b97f0906eb8c41b201df7e2075192c7aba0fe2d2d2dc8c");
		// System.setProperty("javax.net.debug", "all");

		// when
		ios.send(contact, "text", "action", 12);

		// then no exception
	}

	@Test
	public void android() throws Exception {
		// given
		final Contact contact = createContact();
		contact.setPushToken(
				"dHFZR7_iWnc:APA91bF7Z9NsdMRN0nX5C2il8dOqbmJ8DFtAdqb4_2thbOGB0LJK_2m1zjtyXyHD1tmdog6TQsTXbHvKPyv-EuqNik4vM1VlGSY-h6wG6JdM4k9h8es7duf08pfSEYezwuUyGcDkWkQd");

		// when
		android.send(contact, "text", "action");

		// then no exception
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
		for (Object k : keys) {
			if (FAILED_AUTHS.get(k) < 5)
				FAILED_AUTHS.remove(k);
		}

		// then no exception
	}
}