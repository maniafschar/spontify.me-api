package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

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
import com.jq.findapp.util.Strings;

@RestController
@CrossOrigin(origins = { "http://localhost:9000", Strings.URL })
@RequestMapping("authentication")
public class AuthenticationApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private AuthenticationExternalService authenticationExternalService;

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
		if (user != null && "0.9.9".equals(contact.getVersion()))
			user.put("script_correction",
					"window.localStorage.removeItem('findMeIDs');formFunc.validation.badWords=' anal | anus | arsch| ass |bdsm|blowjob| boob|bukkake|bumse|busen| cock | cum |cunnilingus|dildo|ejacul|ejakul|erection|erektion|faschis|fascis|fick|fuck|goebbels|göring|hakenkreuz|himmler|hitler|hure| möse |nazi|neger|nsdap|nutte|orgasm|penis|porn|pussy|queer|schwanz| sex |sucker|tits|titten|vagina|vibrator|vögeln|whore|wigger|wixer'.split('|');formFunc.validation.filterWords=function(t){var e=t.value;if(e){if(e=' '+e+' ',!formFunc.validation.badWordsReplacement){formFunc.validation.badWordsReplacement=[];for(var n=0;n<formFunc.validation.badWords.length;n++){for(var i='',a=0;a<formFunc.validation.badWords[n].length;a++)i+=' '==formFunc.validation.badWords[n].charAt(a)?' ':'*';formFunc.validation.badWordsReplacement.push(i)}}for(n=0;n<formFunc.validation.badWords.length;n++)e=e.replace(new RegExp(formFunc.validation.badWords[n],'ig'),formFunc.validation.badWordsReplacement[n])}(!e||e==' '+t.value+' ')?formFunc.resetError(t):(t.value=e.substring(1,e.length-1),formFunc.setError(t,'filter.offensiveWords'))};lists.openFilter=function(event, html) {var activeID = ui.navigation.getActiveID();if(!lists.data[activeID] || event.target.nodeName == 'LABEL') return;var e = ui.q(activeID + ' filters');if(!e.innerHTML){e.innerHTML = html.call();formFunc.initFields(activeID + ' filters');} if (ui.cssValue(e, 'transform').indexOf('1') < 0) ui.css(e, 'transform', 'scale(1)'); else ui.css(e, 'transform', 'scale(0)');}");
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
