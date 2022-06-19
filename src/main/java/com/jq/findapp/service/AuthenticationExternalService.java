package com.jq.findapp.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
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
		if (registration.getFrom() == From.Apple)
			return registerApple(registration);
		return registerFacebook(registration);
	}

	private Contact registerApple(ExternalRegistration registration) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setUser(new Contact());
		params.getUser().setId(BigInteger.valueOf(0L));
		params.setSearch("contact.appleId='" + registration.getUser().get("id") + '\'');
		Contact c = null;
		Map<String, Object> contact = repository.one(params);
		if (contact == null) {
			registration.getUser().put("email", Encryption.decryptBrowser(registration.getUser().get("email")));
			if (registration.getUser().get("email").contains("@")) {
				params.setSearch("contact.email='" + registration.getUser().get("email") + '\'');
				contact = repository.one(params);
				if (contact == null) {
					c = new Contact();
					c.setAppleId(registration.getUser().get("id"));
					c.setVerified(true);
					c.setEmail(registration.getUser().get("email").trim());
					c.setPseudonym(registration.getUser().get("name"));
					authenticationService.saveRegistration(c, registration);
				} else {
					c = repository.one(Contact.class, new BigInteger(contact.get("contact.id").toString()));
					c.setAppleId(registration.getUser().get("id"));
					repository.save(c);
				}
			}
		} else if (registration.getUser().containsKey("email"))
			c = repository.one(Contact.class, new BigInteger(contact.get("contact.id").toString()));
		return c;
	}

	private Contact registerFacebook(ExternalRegistration registration) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setUser(new Contact());
		params.getUser().setId(BigInteger.valueOf(0L));
		params.setSearch("contact.facebookId='" + registration.getUser().get("id") + '\'');
		Map<String, Object> contact = repository.one(params);
		if (contact == null) {
			if (registration.getUser().get("email") != null)
				registration.getUser().put("email", Encryption.decryptBrowser(registration.getUser().get("email")));
			if (registration.getUser().get("email") == null || !registration.getUser().get("email").contains("@"))
				registration.getUser().put("email", registration.getUser().get("id") + ".facebook@jq-consulting.de");
			params.setSearch("contact.email='" + registration.getUser().get("email") + '\'');
			contact = repository.one(params);
		}
		final Contact c;
		if (contact == null) {
			c = new Contact();
			c.setVerified(true);
			c.setEmail(registration.getUser().get("email"));
			c.setPseudonym(registration.getUser().get("name"));
			fillFacebookData(registration.getUser(), c);
			authenticationService.saveRegistration(c, registration);
		} else {
			c = repository.one(Contact.class, new BigInteger(contact.get("contact.id").toString()));
			fillFacebookData(registration.getUser(), c);
			repository.save(c);
		}
		return c;
	}

	private void fillFacebookData(Map<String, String> facebookData, Contact contact)
			throws Exception {
		contact.setFacebookId(facebookData.get("id"));
		if (facebookData.get("accessToken") != null)
			contact.setFbToken(Encryption.decryptBrowser(facebookData.get("accessToken")));
		if (contact.getImage() == null && facebookData.containsKey("picture")) {
			byte[] data = IOUtils.toByteArray(new URL(facebookData.get("picture")));
			final BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
			if (img.getWidth() > 400 && img.getHeight() > 400) {
				data = EntityUtil.scaleImage(data, EntityUtil.IMAGE_SIZE);
				contact.setImage(Repository.Attachment.createImage(".jpg", data));
				contact.setImageList(Repository.Attachment.createImage(".jpg",
						EntityUtil.scaleImage(data, EntityUtil.IMAGE_THUMB_SIZE)));
			}
		}
		if (facebookData.get("gender") != null)
			contact.setGender("male".equalsIgnoreCase(facebookData.get("gender")) ? (short) 1 : 2);
		if (facebookData.get("birthday") != null && facebookData.get("birthday").length() > 0)
			contact.setBirthday(new Date(
					new SimpleDateFormat("MM/dd/yyyy").parse(facebookData.get("birthday").trim())
							.getTime()));
	}
}
