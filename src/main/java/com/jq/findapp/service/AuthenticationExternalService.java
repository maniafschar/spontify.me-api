package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.model.ExternalRegistration;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.EntityUtil;

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

	public Contact register(ExternalRegistration registration) throws Exception {
		Contact contact = findById(registration);
		if (contact == null)
			contact = findByEmail(registration);
		return contact == null ? registerInternal(registration) : contact;
	}

	private Contact findById(ExternalRegistration registration) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact." + registration.getFrom().name().toLowerCase() + "Id='"
				+ registration.getUser().get("id") + '\'');
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

	private Contact findByEmail(ExternalRegistration registration) throws Exception {
		if (registration.getUser().get("email") != null)
			registration.getUser().put("email", Encryption.decryptBrowser(registration.getUser().get("email")));
		if (registration.getUser().get("email") == null || !registration.getUser().get("email").contains("@"))
			registration.getUser().put("email",
					registration.getUser().get("id") + '.' + registration.getFrom().name().toLowerCase()
							+ "@skillvents.com");
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.email='" + registration.getUser().get("email") + '\'');
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

	private Contact registerInternal(ExternalRegistration registration) throws Exception {
		final Contact c = new Contact();
		c.setVerified(true);
		c.setEmail(registration.getUser().get("email"));
		if (!c.getEmail().endsWith("@skillvents.com"))
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

	private void fillFacebookData(Map<String, String> facebookData, Contact contact)
			throws Exception {
		contact.setFacebookId(facebookData.get("id"));
		if (facebookData.get("accessToken") != null)
			contact.setFbToken(Encryption.decryptBrowser(facebookData.get("accessToken")));
		if (contact.getImage() == null && facebookData.containsKey("picture")
				&& facebookData.get("picture") != null && facebookData.get("picture").startsWith("http")) {
			try {
				contact.setImage(EntityUtil.getImage(facebookData.get("picture"), EntityUtil.IMAGE_SIZE));
				if (contact.getImage() != null)
					contact.setImageList(EntityUtil.getImage(facebookData.get("picture"), EntityUtil.IMAGE_THUMB_SIZE));
			} catch (Exception ex) {
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
