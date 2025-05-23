package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.model.ExternalRegistration;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Entity;

@Service
public class AuthenticationExternalService {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	public enum From {
		Facebook,
		Apple
	}

	public Contact register(final ExternalRegistration registration) throws Exception {
		Contact contact = findById(registration);
		if (contact == null)
			contact = findByEmail(registration);
		return contact == null ? registerInternal(registration) : contact;
	}

	private Contact findById(final ExternalRegistration registration) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact." + registration.getFrom().name().toLowerCase() + "Id='"
				+ registration.getUser().get("id") + "' and contact.clientId=" + registration.getClientId());
		final Map<String, Object> contact = repository.one(params);
		if (contact != null) {
			final Contact c = repository.one(Contact.class, new BigInteger(contact.get("contact.id").toString()));
			if (registration.getFrom() == From.Facebook) {
				fillFacebookData(registration.getUser(), c);
				repository.save(c);
			}
			return c;
		}
		return null;
	}

	private Contact findByEmail(final ExternalRegistration registration) throws Exception {
		if (registration.getUser().get("email") != null)
			registration.getUser().put("email", Encryption.decryptBrowser(registration.getUser().get("email")));
		if (registration.getUser().get("email") == null || !registration.getUser().get("email").contains("@")) {
			final Client client = repository.one(Client.class, registration.getClientId());
			registration.getUser().put("email",
					registration.getUser().get("id") + '.' + registration.getFrom().name().toLowerCase()
							+ client.getEmail().substring(client.getEmail().indexOf('@')));
		}
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + registration.getUser().get("email") + "' and contact.clientId="
				+ registration.getClientId());
		final Map<String, Object> contact = repository.one(params);
		if (contact != null) {
			final Contact c = repository.one(Contact.class, new BigInteger(contact.get("contact.id").toString()));
			if (registration.getFrom() == From.Facebook)
				fillFacebookData(registration.getUser(), c);
			else
				c.setAppleId(registration.getUser().get("id"));
			repository.save(c);
			return c;
		}
		return null;
	}

	private Contact registerInternal(final ExternalRegistration registration) throws Exception {
		final Contact c = new Contact();
		c.setVerified(true);
		c.setEmail(registration.getUser().get("email"));
		if (!c.getEmail().endsWith("@after-work.events"))
			c.setEmailVerified(c.getEmail());
		c.setPseudonym(registration.getUser().get("name").trim());
		if ("".equals(c.getPseudonym()))
			c.setPseudonym("Lucky Luke");
		if (registration.getFrom() == From.Facebook)
			fillFacebookData(registration.getUser(), c);
		else
			c.setAppleId(registration.getUser().get("id"));
		authenticationService.saveRegistration(c, registration);
		return c;
	}

	private void fillFacebookData(final Map<String, String> facebookData, final Contact contact)
			throws Exception {
		contact.setFacebookId(facebookData.get("id"));
		if (facebookData.get("accessToken") != null)
			contact.setFbToken(Encryption.decryptBrowser(facebookData.get("accessToken")));
		if (contact.getImage() == null && facebookData.containsKey("picture")
				&& facebookData.get("picture") != null && facebookData.get("picture").startsWith("http")) {
			try {
				contact.setImage(Entity.getImage(facebookData.get("picture"), Entity.IMAGE_SIZE, 0));
				if (contact.getImage() != null)
					contact.setImageList(
							Entity.getImage(facebookData.get("picture"), Entity.IMAGE_THUMB_SIZE, 0));
			} catch (final Exception ex) {
				// no pic for now, continue registration/login
			}
		}
		if (contact.getGender() == null && facebookData.get("gender") != null)
			contact.setGender("male".equalsIgnoreCase(facebookData.get("gender")) ? (short) 1 : 2);
		if (contact.getBirthday() == null && facebookData.get("birthday") != null
				&& facebookData.get("birthday").length() > 0)
			contact.setBirthday(new Date(
					new SimpleDateFormat("MM/dd/yyyy").parse(facebookData.get("birthday").trim())
							.getTime()));
	}
}
