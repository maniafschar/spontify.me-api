package com.jq.findapp.util;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Repository;

@Component
public class Utils {

	@Autowired
	private Repository repository;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	public Contact createContact() throws Exception {
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
				contact.setVisitPage(new Timestamp(System.currentTimeMillis() - 3000000L));
				contact.setGender((short) 1);
				contact.setPseudonym("pseudonym");
				contact.setVerified(true);
				contact.setPassword(Encryption.encryptDB("secret_password"));
				contact.setPasswordReset(System.currentTimeMillis());
				repository.save(contact);
			} while (contact.getId().intValue() < adminId.intValue());
		}
		return contact;
	}
}
