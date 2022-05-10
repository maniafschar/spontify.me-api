package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

import com.jq.findapp.api.model.ExternalRegistration;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactToken;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationExternalService;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Encryption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = { "https://localhost", "https://findapp.online" })
@RequestMapping("authentication")
public class AuthenticationApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private AuthenticationExternalService authenticationExternalService;

	@GetMapping("logoff")
	public void logoff(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact c = authenticationService.verify(user, password, salt);
		c.setActive(false);
		repository.save(c);
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
		if (user != null && publicKey != null) {
			final ContactToken t;
			final QueryParams params = new QueryParams(Query.contact_token);
			params.setSearch("contactToken.contactId='" + user.get("contact.id") + "' and contactToken.token=''");
			final Result u = repository.list(params);
			if (u.size() < 1) {
				t = new ContactToken();
				t.setContactId((BigInteger) user.get("contact.id"));
			} else {
				t = repository.one(ContactToken.class, (BigInteger) u.get(0).get("contactToken.id"));
				for (int i = 1; i < u.size(); i++)
					repository.delete(repository.one(ContactToken.class, (BigInteger) u.get(i).get("contactToken.id")));
			}
			t.setToken(UUID.randomUUID().toString());
			repository.save(t);
			user.put("auto_login_token", Encryption.encrypt(t.getToken(), publicKey));
		}
		return user;
	}

	@DeleteMapping("one")
	public void one(@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.verify(user, password, salt);
		repository.deleteAccount(user);
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
	public String recoverSendEmail(String email, String name) throws Exception {
		return authenticationService.recoverSendEmail(Encryption.decryptBrowser(email),
				Encryption.decryptBrowser(name));
	}

	@GetMapping("recoverVerifyEmail")
	public String recoverVerifyEmail(String publicKey, String token) throws Exception {
		final Contact contact = authenticationService.recoverVerifyEmail(Encryption.decryptBrowser(token));
		return contact == null ? null
				: Encryption.encrypt(
						contact.getEmail() + "\u0015" + authenticationService.getPassword(contact), publicKey);
	}
}
