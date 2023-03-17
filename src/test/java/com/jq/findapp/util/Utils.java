package com.jq.findapp.util;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Repository;

@Component
public class Utils {

	@Autowired
	private Repository repository;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	public Client createClient() throws Exception {
		Client client = repository.one(Client.class, BigInteger.ONE);
		if (client == null) {
			client = new Client();
			client.setName("abc");
			repository.save(client);
		}
		return client;
	}

	public Contact createContact() throws Exception {
		Contact contact = repository.one(Contact.class, adminId);
		if (contact == null) {
			int i = 1;
			do {
				contact = new Contact();
				contact.setClientId(BigInteger.ONE);
				contact.setEmail("test" + i + "@jq-consulting.de");
				contact.setLanguage("DE");
				contact.setIdDisplay("123456" + i++);
				contact.setFacebookId("1234567890");
				contact.setBirthday(new Date(3000000000L));
				contact.setVisitPage(new Timestamp(System.currentTimeMillis() - 3000000L));
				contact.setGender((short) 1);
				contact.setTimezone(TimeZone.getDefault().getID());
				contact.setPseudonym("pseudonym");
				contact.setVerified(true);
				contact.setPassword(Encryption.encryptDB("secret_password"));
				contact.setPasswordReset(System.currentTimeMillis());
				repository.save(contact);
			} while (contact.getId().intValue() < adminId.intValue());
		}
		return contact;
	}

	public void setEventDate(BigInteger id, Timestamp date) throws Exception {
		String d = date.toInstant().atZone(ZoneId.systemDefault()).toString();
		d = d.substring(0, d.indexOf('.')).replace('T', ' ');
		repository.executeUpdate("update Event set startDate='" + d + "', endDate='"
				+ (d = d.substring(0, d.indexOf(' '))) + "', contactId=1 where id=" + id);
		repository.executeUpdate("update EventParticipate set eventDate='" + d + "' where eventId=" + id);
		repository.executeUpdate("update Contact set latitude=2, longitude=-2 where id=1");
	}

	public void initPaypalSandbox() throws Exception {
		String v = "";
		for (int i = 1; i < 20; i++)
			v += "," + i;
		final Setting setting = new Setting();
		setting.setLabel("paypal.sandbox");
		setting.setValue(v.substring(1));
		repository.save(setting);
	}
}
