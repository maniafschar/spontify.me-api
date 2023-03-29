package com.jq.findapp.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.AbstractRegistration;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService.AuthenticationException.AuthenticationExceptionType;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

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
	private final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
			Pattern.CASE_INSENSITIVE);

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

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

		private Unique(String email, boolean unique, boolean blocked) {
			this.email = email;
			this.unique = unique;
			this.blocked = blocked;
		}
	}

	private static class FailedAttempts {
		private long timestamp = System.currentTimeMillis();
		private int attempts = 1;
	}

	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public static class AuthenticationException extends RuntimeException {
		public enum AuthenticationExceptionType {
			NoInputFromClient, WrongPassword, NoPasswordInDB, UsedSalt, Unknown
		}

		private final AuthenticationExceptionType type;

		private AuthenticationException(AuthenticationExceptionType type) {
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
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public Contact verify(BigInteger user, String password, String salt) {
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
				for (Object k : keys) {
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
				} catch (InterruptedException e) {
				}
			}
			throw new AuthenticationException(AuthenticationExceptionType.WrongPassword);
		}
		return contact;
	}

	public void register(InternalRegistration registration) throws Exception {
		if (!registration.isAgb())
			throw new IllegalAccessException("legal");
		final int minimum = 5000;
		if (registration.getTime() < minimum)
			throw new IllegalAccessException("time");
		if (!EMAIL.matcher(registration.getEmail()).find())
			throw new IllegalAccessException("email");
		final Unique unique = unique(registration.getEmail());
		if (!unique.unique)
			throw new IllegalAccessException("email");
		if (unique.blocked) {
			notificationService.createTicket(TicketType.ERROR, "denied email blocked", registration.toString(), null);
			throw new IllegalAccessException("domain");
		}
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + registration.getEmail().toLowerCase().trim() + '\'');
		final Map<String, Object> user = repository.one(params);
		final Contact contact = user == null ? new Contact()
				: repository.one(Contact.class, (BigInteger) user.get("contact.id"));
		contact.setBirthday(registration.getBirthday());
		contact.setGender(registration.getGender());
		contact.setPseudonym(registration.getPseudonym());
		contact.setLanguage(registration.getLanguage());
		contact.setEmail(registration.getEmail().toLowerCase().trim());
		contact.setTimezone(registration.getTimezone());
		try {
			notificationService.sendNotificationEmail(repository.one(Contact.class, adminId), contact,
					Text.mail_contactWelcomeEmail.getText(contact.getLanguage()), "r=" + generateLoginParam(contact));
			saveRegistration(contact, registration);
		} catch (SendFailedException ex) {
			throw new IllegalAccessException("email");
		}
	}

	public Unique unique(String email) {
		email = email.toLowerCase();
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("LOWER(contact.email)='" + email + "'");
		return new Unique(email, repository.one(params) == null, AuthenticationService.BLOCKED_EMAIL_DOMAINS
				.contains(email.substring(email.indexOf('@') + 1)));
	}

	void saveRegistration(Contact contact, AbstractRegistration registration) throws Exception {
		contact.setDevice(registration.getDevice());
		contact.setLanguage(registration.getLanguage());
		contact.setClientId(registration.getClientId());
		contact.setOs(registration.getOs());
		contact.setVersion(registration.getVersion());
		contact.setVisitPage(new Timestamp(Instant.now().toEpochMilli() - 1000));
		contact.setPassword(Encryption.encryptDB(generatePin(20)));
		contact.setPasswordReset(Instant.now().toEpochMilli());
		contact.setBirthdayDisplay((short) 2);
		contact.setTimezone(
				registration.getTimezone() == null ? TimeZone.getDefault().getID() : registration.getTimezone());
		contact.setEmail(contact.getEmail().toLowerCase().trim());
		if (contact.getIdDisplay() == null) {
			final String[] name = contact.getPseudonym().split(" ");
			int max = 100, i = 0;
			while (true) {
				try {
					contact.setIdDisplay(generateIdDisplay(name));
					repository.save(contact);
					break;
				} catch (Throwable ex) {
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
		notificationService.createTicket(TicketType.REGISTRATION, contact.getEmail(),
				registration.toString(), contact.getId());
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
		params.setSearch("contact.email='" + contact.getEmail() + "'");
		params.setUser(new Contact());
		params.getUser().setId(BigInteger.valueOf(0L));
		params.getUser().setClientId(contact.getClientId());
		final Map<String, Object> user = repository.one(params);
		if (user != null) {
			final Contact c2 = repository.one(Contact.class, (BigInteger) user.get("contact.id"));
			verify(c2, password, salt, true);
			c2.setActive(true);
			c2.setLastLogin(new Timestamp(Instant.now().toEpochMilli()));
			c2.setOs(contact.getOs());
			c2.setDevice(contact.getDevice());
			c2.setVersion(contact.getVersion());
			if (contact.getTimezone() != null)
				c2.setTimezone(contact.getTimezone());
			repository.save(c2);
		}
		return user;
	}

	private String generateLoginParam(Contact contact) {
		final String s = generatePin(42);
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

	private String generateIdDisplay(String[] name) {
		String s = "";
		int i = -1, max = 6;
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

	public String getAutoLogin(String publicKey, String token) {
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
		} catch (Exception ex) {
			return null;
		}
	}

	public void logoff(Contact contact, String token) throws Exception {
		contact.setActive(false);
		repository.save(contact);
		if (token != null) {
			final QueryParams params = new QueryParams(Query.contact_token);
			params.setSearch("contactToken.token='" + Encryption.decryptBrowser(token) + "'");
			final Map<String, Object> u = repository.one(params);
			if (u != null)
				repository.delete(repository.one(ContactToken.class, (BigInteger) u.get("contactToken.id")));
		}
	}

	public void recoverSendEmailReminder(Contact contact) throws Exception {
		final String s;
		if (Strings.isEmpty(contact.getLoginLink())) {
			s = generateLoginParam(contact);
			repository.save(contact);
		} else
			s = contact.getLoginLink().substring(0, 10) + contact.getLoginLink().substring(20);
		notificationService.sendNotificationEmail(repository.one(Contact.class, adminId),
				contact,
				Text.mail_contactRegistrationReminder.getText(contact.getLanguage()).replace("<jq:EXTRA_1 />",
						Strings.formatDate(null, contact.getCreatedAt(), contact.getTimezone())),
				"r=" + s);
	}

	public String recoverSendEmail(String email) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + email + '\'');
		final Map<String, Object> user = repository.one(params);
		if (user != null) {
			final Contact contact = repository.one(Contact.class, (BigInteger) user.get("contact.id"));
			final String s = generateLoginParam(contact);
			repository.save(contact);
			notificationService.sendNotificationEmail(contact, contact,
					Text.mail_contactPasswordReset.getText(contact.getLanguage()), "r=" + s);
			return "ok";
		}
		return "nok:Email";
	}

	public Contact recoverVerifyEmail(String token) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.loginLink like '%" + token + "%'");
		final Map<String, Object> user = repository.one(params);
		if (user == null)
			return null;
		final Contact contact = repository.one(Contact.class, new BigInteger(user.get("contact.id").toString()));
		if (contact.getVerified() == null || !contact.getVerified()) {
			contact.setVerified(Boolean.TRUE);
			contact.setNotificationEngagement(Boolean.TRUE);
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
		} catch (NoSuchAlgorithmException e) {
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
				} catch (Exception e) {
					throw new RuntimeException(u.getId() + ": " + u.getPassword(), e);
				}
			}
			return pw.password;
		}
	}

	public void deleteAccount(final Contact user) throws Exception {
		if (user.getId() == adminId)
			return;
		final String[] sqls = accountDeleteSql.split(";");
		for (String sql : sqls) {
			try {
				sql = sql.replaceAll("\\{ID}", "" + user.getId()).trim();
				if (sql.startsWith("from ")) {
					final List<?> list = repository.list(sql);
					for (Object e : list)
						repository.delete((BaseEntity) e);
				} else
					repository.executeUpdate(sql);
			} catch (Exception ex) {
				throw new RuntimeException("ERROR SQL on account delete: " + ex.getMessage() + "\n" + sql);
			}
		}
	}

	private String generatePin(final int length) {
		final StringBuilder s = new StringBuilder();
		char c;
		while (s.length() < length) {
			c = (char) (Math.random() * 150);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
				s.append(c);
		}
		return s.toString();
	}
}
