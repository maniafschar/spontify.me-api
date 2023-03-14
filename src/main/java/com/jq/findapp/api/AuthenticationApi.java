package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.transaction.Transactional;
import javax.ws.rs.core.Context;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.api.model.ExternalRegistration;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationExternalService;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Strings;

@RestController
@Transactional
@CrossOrigin(origins = { Strings.URL_APP, Strings.URL_APP_NEW, Strings.URL_LOCALHOST, Strings.URL_LOCALHOST_TEST })
@RequestMapping("authentication")
public class AuthenticationApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private AuthenticationExternalService authenticationExternalService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@GetMapping("logoff")
	public void logoff(String token, @RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.logoff(authenticationService.verify(user, password, salt), token);
	}

	@PostMapping("register")
	public void register(@RequestBody final InternalRegistration registration) throws Exception {
		authenticationService.register(registration);
	}

	@GetMapping("login")
	public Map<String, Object> login(Contact contact, String publicKey, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		contact.setEmail(Encryption.decryptBrowser(contact.getEmail()));
		final Map<String, Object> user = authenticationService.login(contact, password, salt);
		if (user != null) {
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("location.contactId=" + user.get("contact.id"));
			user.put("location_added", repository.list(params).size());
			if (getVideoTimeSlot((BigInteger) user.get("contact.id")))
				user.put("login_video_call", Boolean.TRUE);
			params.setQuery(Query.contact_listGeoLocationHistory);
			params.setSearch("contactGeoLocationHistory.contactId=" + user.get("contact.id"));
			final Result result = repository.list(params);
			if (result.size() > 0) {
				final Map<String, Object> geoLocationHistory = result.get(0);
				if (geoLocationHistory.containsKey("contactGeoLocationHistory.manual")
						&& ((Boolean) geoLocationHistory.get("contactGeoLocationHistory.manual"))) {
					final GeoLocation geoLocation = repository.one(GeoLocation.class,
							(BigInteger) geoLocationHistory.get("contactGeoLocationHistory.geoLocationId"));
					user.put("geo_location", "{\"lat\":" + geoLocation.getLatitude() +
							",\"lon\":" + geoLocation.getLongitude()
							+ ",\"street\":\"" + geoLocation.getStreet() +
							"\",\"town\":\"" + geoLocation.getTown() + "\"}");
				}
			}
			if (publicKey != null) {
				final ContactToken t;
				params.setQuery(Query.contact_token);
				params.setSearch("contactToken.contactId=" + user.get("contact.id") + " and contactToken.token=''");
				final Result u = repository.list(params);
				if (u.size() < 1) {
					t = new ContactToken();
					t.setContactId((BigInteger) user.get("contact.id"));
				} else {
					t = repository.one(ContactToken.class, (BigInteger) u.get(0).get("contactToken.id"));
					for (int i = 1; i < u.size(); i++)
						repository.delete(
								repository.one(ContactToken.class, (BigInteger) u.get(i).get("contactToken.id")));
				}
				t.setToken(UUID.randomUUID().toString());
				repository.save(t);
				user.put("auto_login_token", Encryption.encrypt(t.getToken(), publicKey));
			}
		}
		return user;
	}

	private Boolean getVideoTimeSlot(BigInteger id) {
		if (adminId.equals(id))
			return true;
		final QueryParams params = new QueryParams(Query.contact_listVideoCalls);
		params.setUser(repository.one(Contact.class, id));
		params.setSearch("contactVideoCall.contactId=" + id);
		final Result list = repository.list(params);
		if (list.size() == 0)
			return false;
		final Timestamp t = (Timestamp) list.get(0).get("contactVideoCall.time");
		return t.getTime() > System.currentTimeMillis() - 600000 && t.getTime() < System.currentTimeMillis() + 600000;
	}

	@DeleteMapping("one")
	public void one(@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.deleteAccount(authenticationService.verify(user, password, salt));
	}

	@PutMapping("loginExternal")
	public String loginExternal(@RequestBody final ExternalRegistration registration) throws Exception {
		registration.getUser().put("id", Encryption.decryptBrowser(registration.getUser().get("id")));
		final Contact contact = authenticationExternalService.register(registration);
		return contact == null ? null
				: Encryption.encrypt(contact.getEmail() + "\u0015" + authenticationService.getPassword(contact),
						registration.getPublicKey());
	}

	@GetMapping("loginAuto")
	public String autoLogin(String publicKey, String token) throws IllegalAccessException {
		return authenticationService.getAutoLogin(publicKey, token);
	}

	@GetMapping("recoverSendEmail")
	public String recoverSendEmail(String email, @Context HttpHeaders httpHeaders) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setSearch("log.ip='" + httpHeaders.get("X-Forwarded-For")
				+ "' and log.createdAt>'" + Instant.now().minus(Duration.ofDays(1)).toString()
				+ "' and log.uri like '%recoverSendEmail'");
		if (repository.list(params).size() > 10)
			return "nok:Spam";
		return authenticationService.recoverSendEmail(Encryption.decryptBrowser(email));
	}

	@GetMapping("recoverVerifyEmail")
	public String recoverVerifyEmail(String publicKey, String token) throws Exception {
		final Contact contact = authenticationService.recoverVerifyEmail(Encryption.decryptBrowser(token));
		return contact == null ? null
				: Encryption.encrypt(
						contact.getEmail() + "\u0015" + authenticationService.getPassword(contact), publicKey);
	}
}
