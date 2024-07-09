package com.jq.findapp.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.AbstractRegistration;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.ContactLink;
import com.jq.findapp.entity.ContactLink.Status;
import com.jq.findapp.entity.ContactReferer;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService.AuthenticationException.AuthenticationExceptionType;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

import jakarta.mail.SendFailedException;

@Service
public class AuthenticationService {
	private static final List<String> USED_SALTS = new ArrayList<>();
	private static final List<String> BLOCKED_EMAIL_DOMAINS = new ArrayList<>();
	private static final Map<BigInteger, Password> PW = new HashMap<>();
	private static final Map<BigInteger, FailedAttempts> FAILED_AUTHS = new HashMap<>();
	private static final char[] idChars = { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
			'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
			'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '0', '1', '2', '3', '4',
			'5', '6', '7', '8', '9' };
	private static long TIMEOUT = 3600000L;

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	@Value("${account.delete.sql}")
	private String accountDeleteSql;

	private static class Password {
		private final String password;
		private final long reset;

		private Password(final String password, final long reset) {
			this.password = password;
			this.reset = reset;
		}
	}

	public static class Unique {
		public final String email;
		public final boolean unique;
		public final boolean blocked;

		private Unique(final String email, final boolean unique, final boolean blocked) {
			this.email = email;
			this.unique = unique;
			this.blocked = blocked;
		}
	}

	private static class FailedAttempts {
		private final long timestamp = System.currentTimeMillis();
		private int attempts = 1;
	}

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public static class AuthenticationException extends RuntimeException {
		public enum AuthenticationExceptionType {
			NoInputFromClient, WrongPassword, NoPasswordInDB, UsedSalt, WrongClient, ProtectetdArea, Unknown
		}

		private final AuthenticationExceptionType type;

		public AuthenticationException(final AuthenticationExceptionType type) {
			this.type = type == null ? AuthenticationExceptionType.Unknown : type;
		}

		public AuthenticationExceptionType getType() {
			return type;
		}

		@Override
		public String getMessage() {
			return type.name();
		}
	}

	static {
		try {
			BLOCKED_EMAIL_DOMAINS.addAll(Arrays.asList(new ObjectMapper().readValue(
					AuthenticationService.class.getResourceAsStream("/blockedEmailDomains.json"),
					String[].class)));
		} catch (final Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public Contact verify(final BigInteger user, final String password, final String salt) {
		if (user == null || user.compareTo(BigInteger.ONE) < 0)
			throw new AuthenticationException(AuthenticationExceptionType.NoInputFromClient);
		return verify(repository.one(Contact.class, user), password, salt, false);
	}

	private Contact verify(final Contact contact, final String password, final String salt, final boolean login) {
		if (contact == null || password == null || password.length() == 0 || salt == null || salt.length() == 0)
			throw new AuthenticationException(AuthenticationExceptionType.NoInputFromClient);
		synchronized (USED_SALTS) {
			if (USED_SALTS.contains(salt))
				throw new AuthenticationException(AuthenticationExceptionType.UsedSalt);
			USED_SALTS.add(salt);
			if (USED_SALTS.size() > 1000) {
				final long timeout = System.currentTimeMillis() - TIMEOUT;
				final char sep = '.';
				String s;
				int i = 0;
				while (USED_SALTS.size() > i) {
					s = USED_SALTS.get(i);
					if (Long.valueOf(s.substring(0, s.indexOf(sep))) < timeout)
						USED_SALTS.remove(i);
					else
						i++;
				}
			}
		}
		if (!getHash(getPassword(contact) + salt + (login ? 0 : contact.getId())).equals(password)) {
			final FailedAttempts x;
			synchronized (FAILED_AUTHS) {
				final long SIX_HOURS_BEFORE = System.currentTimeMillis() - 3600000L * 6L;
				final Object[] keys = FAILED_AUTHS.keySet().toArray();
				for (final Object k : keys) {
					if (FAILED_AUTHS.get(k).timestamp < SIX_HOURS_BEFORE)
						FAILED_AUTHS.remove(k);
				}
				x = FAILED_AUTHS.get(contact.getId());
				if (x == null)
					FAILED_AUTHS.put(contact.getId(), new FailedAttempts());
				else
					FAILED_AUTHS.get(contact.getId()).attempts++;
			}
			if (x != null && x.attempts > 5) {
				try {
					Thread.sleep((long) Math.pow(x.attempts - 5, 3) * 1000L);
				} catch (final InterruptedException e) {
				}
			}
			throw new AuthenticationException(AuthenticationExceptionType.WrongPassword);
		}
		return contact;
	}

	public Contact register(final InternalRegistration registration) throws Exception {
		if (!registration.isAgb())
			throw new IllegalAccessException("legal");
		final int minimum = 5000;
		if (registration.getTime() < minimum)
			throw new IllegalAccessException("time");
		if (!Strings.isEmail(registration.getEmail()))
			throw new IllegalAccessException("email");
		final Unique unique = unique(registration.getClientId(), registration.getEmail());
		if (!unique.unique)
			throw new IllegalAccessException("email");
		if (unique.blocked) {
			notificationService.createTicket(TicketType.ERROR, "denied email blocked", registration.toString(), null);
			throw new IllegalAccessException("domain");
		}
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + registration.getEmail().toLowerCase().trim() + "' and contact.clientId="
				+ registration.getClientId());
		final Map<String, Object> user = repository.one(params);
		final Contact contact = user == null ? new Contact()
				: repository.one(Contact.class, (BigInteger) user.get("contact.id"));
		contact.setBirthday(registration.getBirthday());
		contact.setGender(registration.getGender());
		contact.setPseudonym(registration.getPseudonym());
		contact.setLanguage(registration.getLanguage());
		contact.setEmail(registration.getEmail().toLowerCase().trim());
		contact.setTimezone(registration.getTimezone());
		contact.setClientId(registration.getClientId());
		try {
			notificationService.sendNotificationEmail(
					repository.one(Contact.class,
							repository.one(Client.class, registration.getClientId()).getAdminId()),
					contact,
					text.getText(contact, TextId.mail_contactWelcomeEmail), "r=" + generateLoginParam(contact));
			saveRegistration(contact, registration);
			return contact;
		} catch (final SendFailedException ex) {
			throw new IllegalAccessException("email");
		}
	}

	public Unique unique(final BigInteger clientId, String email) {
		email = email.toLowerCase();
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("LOWER(contact.email)='" + email + "' and contact.clientId=" + clientId);
		return new Unique(email, repository.one(params) == null, AuthenticationService.BLOCKED_EMAIL_DOMAINS
				.contains(email.substring(email.indexOf('@') + 1)));
	}

	void saveRegistration(final Contact contact, final AbstractRegistration registration) throws Exception {
		contact.setDevice(registration.getDevice());
		contact.setLanguage(registration.getLanguage());
		contact.setClientId(registration.getClientId());
		contact.setOs(registration.getOs());
		contact.setVersion(registration.getVersion());
		contact.setReferer(registration.getReferer());
		contact.setVisitPage(new Timestamp(Instant.now().toEpochMilli() - 1000));
		contact.setPassword(Encryption.encryptDB(Strings.generatePin(20)));
		contact.setPasswordReset(Instant.now().toEpochMilli());
		contact.setBirthdayDisplay((short) 2);
		contact.setTimezone(
				registration.getTimezone() == null ? TimeZone.getDefault().getID() : registration.getTimezone());
		contact.setEmail(contact.getEmail().toLowerCase().trim());
		if (contact.getReferer() == null && contact.getOs() != OS.web && registration.getFootprint() != null) {
			final QueryParams params = new QueryParams(Query.contact_listReferer);
			params.setSearch("contactReferer.ip='" + registration.getIp() + "' and contactReferer.footprint='"
					+ registration.getFootprint() + "'");
			final Result result = repository.list(params);
			if (result.size() > 0) {
				final ContactReferer contactReferer = repository.one(ContactReferer.class,
						(BigInteger) result.get(0).get("contactReferer.id"));
				contact.setReferer(contactReferer.getContactId());
				if (contactReferer.getCreatedAt()
						.after(new Date(Instant.now().minus(Duration.ofHours(12)).toEpochMilli())))
					repository.delete(contactReferer);
			}
		}
		if (contact.getIdDisplay() == null) {
			final String[] name = contact.getPseudonym().split(" ");
			final int max = 100;
			int i = 0;
			while (true) {
				try {
					contact.setIdDisplay(generateIdDisplay(name));
					repository.save(contact);
					break;
				} catch (final Throwable ex) {
					if (isDuplicateIdDisplay(ex)) {
						if (i++ > max)
							throw new IllegalAccessException(
									"reg failed: " + i + " tries to find id_display | " + ex.getMessage());
					} else
						throw new IllegalAccessException("reg failed: " + Strings.stackTraceToString(ex));
				}
			}
		} else
			repository.save(contact);
		notificationService.createTicket(TicketType.REGISTRATION, contact.getEmail(), registration.toString(),
				contact.getId());
	}

	private boolean isDuplicateIdDisplay(Throwable ex) {
		while (ex != null && !(ex instanceof SQLIntegrityConstraintViolationException))
			ex = ex.getCause();
		if (ex != null)
			return ex.getMessage().toLowerCase().contains("id_display");
		return false;
	}

	public Map<String, Object> login(final Contact contact, final String password, final String salt) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setSearch("contact.email='" + contact.getEmail() + "' and contact.clientId=" + contact.getClientId());
		params.setUser(new Contact());
		params.getUser().setId(BigInteger.valueOf(0L));
		params.getUser().setClientId(contact.getClientId());
		final Map<String, Object> user = repository.one(params);
		if (user != null) {
			final Contact c2 = repository.one(Contact.class, (BigInteger) user.get("contact.id"));
			verify(c2, password, salt, true);
			c2.setLastLogin(new Timestamp(Instant.now().toEpochMilli()));
			c2.setOs(contact.getOs());
			c2.setDevice(contact.getDevice());
			c2.setVersion(contact.getVersion());
			if (contact.getTimezone() != null)
				c2.setTimezone(contact.getTimezone());
			repository.save(c2);
			if (ContactType.demo == c2.getType())
				user.put("contact.type", ContactType.adminContent.name());
		}
		return user;
	}

	private String generateLoginParam(final Contact contact) {
		final String s = Strings.generatePin(42);
		long x = 0;
		for (int i = 0; i < s.length(); i++) {
			x += s.charAt(i);
			if (x > 99999999)
				break;
		}
		String s2 = "" + x;
		s2 += s.substring(1, 11 - s2.length());
		String old = contact.getLoginLink();
		if (old == null)
			old = "";
		else if (old.length() > 207)
			old = old.substring(52);
		contact.setLoginLink(old + s.substring(0, 10) + s2 + s.substring(10));
		return s;
	}

	private String generateIdDisplay(final String[] name) {
		String s = "";
		int i = -1;
		final int max = 6;
		while (s.length() < max && name.length > ++i) {
			s += name[i].replaceAll("[^a-zA-Z0-9]", "");
			if (name[i].length() > 0)
				name[i] = name[i].substring(0, Math.min(name[i].length() - 1, max - 1));
		}
		if (s.length() > max)
			s = s.substring(0, max);
		else {
			while (s.length() < max)
				s += idChars[(int) (Math.random() * idChars.length)];
		}
		return s;
	}

	public String getAutoLogin(final String publicKey, String token) {
		try {
			token = Encryption.decryptBrowser(token);
			final QueryParams params = new QueryParams(Query.contact_token);
			params.setSearch("contactToken.token='" + token + "'");
			final Map<String, Object> u = repository.one(params);
			if (u == null)
				return null;
			final ContactToken t = repository.one(ContactToken.class, (BigInteger) u.get("contactToken.id"));
			final Contact c = repository.one(Contact.class, t.getContactId());
			t.setToken("");
			repository.save(t);
			return Encryption.encrypt(c.getEmail() + "\u0015" + getPassword(c), publicKey);
		} catch (final Exception ex) {
			return null;
		}
	}

	public void logoff(final Contact contact, final String token) throws Exception {
		if (token != null) {
			final QueryParams params = new QueryParams(Query.contact_token);
			params.setSearch("contactToken.token='" + Encryption.decryptBrowser(token) + "'");
			final Map<String, Object> u = repository.one(params);
			if (u != null)
				repository.delete(repository.one(ContactToken.class, (BigInteger) u.get("contactToken.id")));
		}
	}

	public void recoverSendEmailReminder(final Contact contact) throws Exception {
		final String s;
		if (Strings.isEmpty(contact.getLoginLink())) {
			s = generateLoginParam(contact);
			repository.save(contact);
		} else
			s = contact.getLoginLink().substring(0, 10) + contact.getLoginLink().substring(20);
		notificationService.sendNotificationEmail(
				repository.one(Contact.class, repository.one(Client.class, contact.getClientId()).getAdminId()),
				contact, text.getText(contact, TextId.mail_contactRegistrationReminder).replace("<jq:EXTRA_1 />",
						Strings.formatDate(null, contact.getCreatedAt(), contact.getTimezone())),
				"r=" + s);
	}

	public String recoverSendEmail(final String email, BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + email + "' and contact.clientId=" + clientId);
		final Map<String, Object> user = repository.one(params);
		if (user != null) {
			final Contact contact = repository.one(Contact.class, (BigInteger) user.get("contact.id"));
			final String s = generateLoginParam(contact);
			repository.save(contact);
			notificationService.sendNotificationEmail(contact, contact,
					text.getText(contact, TextId.mail_contactPasswordReset), "r=" + s);
			return "ok";
		}
		return "nok:Email";
	}

	public Contact recoverVerifyEmail(final String token, BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.loginLink like '%" + token + "%' and contact.clientId=" + clientId);
		final Map<String, Object> user = repository.one(params);
		if (user == null)
			return null;
		final Contact contact = repository.one(Contact.class, new BigInteger(user.get("contact.id").toString()));
		if (contact.getVerified() == null || !contact.getVerified()) {
			contact.setVerified(Boolean.TRUE);
			if (contact.getReferer() != null) {
				params.setQuery(Query.contact_listFriends);
				params.setUser(contact);
				params.setId(contact.getReferer());
				params.setSearch(null);
				if (repository.list(params).size() == 0) {
					final ContactLink contactLink = new ContactLink();
					contactLink.setContactId(contact.getReferer());
					contactLink.setContactId2(contact.getId());
					contactLink.setStatus(Status.Friends);
					repository.save(contactLink);
				}
			}
		}
		contact.setEmailVerified(contact.getEmail());
		repository.save(contact);
		return contact;
	}

	private String getHash(final String s) {
		try {
			final byte[] hash = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			final StringBuilder hexString = new StringBuilder();
			for (int i = 0; i < hash.length; i++) {
				final String hex = Integer.toHexString(0xff & hash[i]);
				if (hex.length() == 1)
					hexString.append('0');
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (final NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public String getPassword(final Contact u) {
		if (u.getPassword() == null || u.getPassword().length() == 0)
			throw new AuthenticationException(AuthenticationExceptionType.NoPasswordInDB);
		synchronized (PW) {
			Password pw = PW.get(u.getId());
			if (pw == null || pw.reset - u.getPasswordReset() < 0) {
				try {
					pw = new Password(Encryption.decryptDB(u.getPassword()), u.getPasswordReset());
					PW.put(u.getId(), pw);
				} catch (final Exception e) {
					throw new RuntimeException(u.getId() + ": " + u.getPassword(), e);
				}
			}
			return pw.password;
		}
	}

	public void deleteAccount(final Contact contact) throws Exception {
		if (contact.getId() == repository.one(Client.class, contact.getClientId()).getAdminId())
			return;
		final String[] sqls = accountDeleteSql.split(";");
		for (String sql : sqls) {
			try {
				sql = sql.replaceAll("\\{ID}", "" + contact.getId()).trim();
				if (sql.startsWith("from ")) {
					final List<?> list = repository.list(sql);
					for (final Object e : list)
						repository.delete((BaseEntity) e);
				} else
					repository.executeUpdate(sql);
			} catch (final Exception ex) {
				throw new RuntimeException("ERROR SQL on account delete: " + ex.getMessage() + "\n" + sql);
			}
		}
	}
}
