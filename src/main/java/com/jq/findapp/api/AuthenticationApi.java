package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
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
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationExternalService;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.IpService;
import com.jq.findapp.util.Encryption;

import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("authentication")
public class AuthenticationApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private AuthenticationExternalService authenticationExternalService;

	@GetMapping("logoff")
	public void logoff(final String token, @RequestHeader final BigInteger user) throws Exception {
		this.authenticationService.logoff(this.repository.one(Contact.class, user),
				token == null ? null : Encryption.decryptBrowser(token));
	}

	@PostMapping("register")
	public void register(@RequestBody final InternalRegistration registration, @RequestHeader final BigInteger clientId,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		registration.setClientId(clientId);
		registration.setIp(ip);
		this.authenticationService.register(registration);
	}

	@GetMapping("login")
	public Map<String, Object> login(final Contact contact, final String publicKey, final String token,
			@RequestHeader final BigInteger clientId, @RequestHeader final String password,
			@RequestHeader final String salt) throws Exception {
		contact.setEmail(Encryption.decryptBrowser(contact.getEmail()));
		contact.setClientId(clientId);
		final Map<String, Object> user = this.authenticationService.login(contact, password, salt);
		if (user != null) {
			final QueryParams params = new QueryParams(Query.event_listId);
			params.setSearch("event.contactId=" + user.get("contact.id"));
			int i = this.repository.list(params).size();
			if (i < 5) {
				params.setQuery(Query.location_listId);
				params.setSearch("location.contactId=" + user.get("contact.id"));
				i += this.repository.list(params).size();
			}
			user.put("authority", i > 4 ? "editLocation" : "editLocation[" + i + "]");
			if (this.getVideoTimeSlot((BigInteger) user.get("contact.id")))
				user.put("login_video_call", Boolean.TRUE);
			params.setQuery(Query.contact_listGeoLocationHistory);
			params.setSearch("contactGeoLocationHistory.contactId=" + user.get("contact.id"));
			this.repository.list(params).forEach(e -> {
				if (e.containsKey("contactGeoLocationHistory.manual")
						&& ((Boolean) e.get("contactGeoLocationHistory.manual"))) {
					final GeoLocation geoLocation = this.repository.one(GeoLocation.class,
							(BigInteger) e.get("contactGeoLocationHistory.geoLocationId"));
					user.put("geo_location", "{\"lat\":" + geoLocation.getLatitude() +
							",\"lon\":" + geoLocation.getLongitude()
							+ ",\"street\":\"" + geoLocation.getStreet() +
							"\",\"town\":\"" + geoLocation.getTown() + "\"}");
				}
			});
			if (publicKey != null) {
				params.setQuery(Query.contact_token);
				params.setSearch("contactToken.contactId=" + user.get("contact.id") + " and contactToken.token=''");
				final Result u = this.repository.list(params);
				final ContactToken token;
				if (u.size() < 1) {
					token = new ContactToken();
					token.setContactId((BigInteger) user.get("contact.id"));
				} else
					token = this.repository.one(ContactToken.class, (BigInteger) u.get(0).get("contactToken.id"));
				token.setToken(UUID.randomUUID().toString());
				this.repository.save(token);
				user.put("auto_login_token", Encryption.encrypt(token.getToken(), publicKey));
			}
		}
		return user;
	}

	private Boolean getVideoTimeSlot(final BigInteger id) {
		if (this.repository.one(Client.class, this.repository.one(Contact.class, id).getClientId()).getAdminId()
				.equals(id))
			return true;
		final QueryParams params = new QueryParams(Query.contact_listVideoCalls);
		params.setUser(this.repository.one(Contact.class, id));
		params.setSearch("contactVideoCall.contactId=" + id);
		final Result list = this.repository.list(params);
		if (list.size() == 0)
			return false;
		final Timestamp t = (Timestamp) list.get(0).get("contactVideoCall.time");
		return t.getTime() > System.currentTimeMillis() - 600000 && t.getTime() < System.currentTimeMillis() + 600000;
	}

	@DeleteMapping("one")
	public void one(@RequestHeader final BigInteger user) throws Exception {
		this.authenticationService.deleteAccount(this.repository.one(Contact.class, user));
	}

	@PutMapping("loginExternal")
	public String loginExternal(@RequestBody final ExternalRegistration registration,
			@RequestHeader final BigInteger clientId,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		registration.setClientId(clientId);
		registration.setIp(ip);
		registration.getUser().put("id", Encryption.decryptBrowser(registration.getUser().get("id")));
		final Contact contact = this.authenticationExternalService.register(registration);
		return contact == null ? null
				: Encryption.encrypt(contact.getEmail() + "\u0015" + this.authenticationService.getPassword(contact),
						registration.getPublicKey());
	}

	@GetMapping("loginAuto")
	public String autoLogin(final String publicKey, final String token) throws IllegalAccessException {
		return this.authenticationService.autoLogin(publicKey, Encryption.decryptBrowser(token));
	}

	@GetMapping("resetToken")
	public void resetToken(final String token) throws IllegalAccessException {
		this.authenticationService.resetToken(Encryption.decryptBrowser(token));
	}

	@GetMapping("recoverSendEmail")
	public String recoverSendEmail(final String email,
			@RequestHeader final BigInteger clientId,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setSearch("log.ip='" + IpService.sanatizeIp(ip)
				+ "' and log.createdAt>cast('" + Instant.now().minus(Duration.ofDays(1)).toString()
				+ "' as timestamp) and log.uri like '%recoverSendEmail'");
		if (this.repository.list(params).size() > 10)
			return "nok:Spam";
		return this.authenticationService.recoverSendEmail(Encryption.decryptBrowser(email), clientId);
	}

	@GetMapping("recoverVerifyEmail")
	public String recoverVerifyEmail(final String publicKey, final String token,
			@RequestHeader final BigInteger clientId) throws Exception {
		final Contact contact = this.authenticationService.recoverVerifyEmail(Encryption.decryptBrowser(token),
				clientId);
		return contact == null ? null
				: Encryption.encrypt(
						contact.getEmail() + "\u0015" + this.authenticationService.getPassword(contact), publicKey);
	}
}
