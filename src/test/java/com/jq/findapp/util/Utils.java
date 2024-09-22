package com.jq.findapp.util;

import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Repository;

@Component
public class Utils {

	@Autowired
	private Repository repository;

	private Client createClient(final BigInteger id) throws Exception {
		Client client = repository.one(Client.class, id);
		if (client == null) {
			do {
				client = new Client();
				client.setName("abc");
				client.setEmail("abc@jq-consulting.de");
				client.setUrl("https://fan-club.online");
				client.setStorage(
						"{\"lang\":{\"DE\":{\"buddy\":\"Buddy\",\"buddies\":\"Buddies\"}},\"rss\":[{\"url\":\"https://newsfeed.kicker.de/team/fc-bayern-muenchen\",\"publish\":false,\"latitude\":48.13684,\"longitude\":11.57685,\"descriptionPostfix\":\"Kicker\"}],\"css\":{\"bg1stop\":\"rgb(255,4,250)\",\"bg1start\":\"rgb(80,0,75)\",\"text\":\"rgb(255,255,255)\"},\"matchDays\":[0]}");
				client.setAdminId(id);
				repository.save(client);
			} while (client.getId().compareTo(id) < 0);
		}
		return client;
	}

	public Contact createContact(final BigInteger id) throws Exception {
		Contact contact = repository.one(Contact.class, id);
		if (contact == null) {
			do {
				createClient(id);
				contact = new Contact();
				contact.setClientId(id);
				contact.setEmail("test1@jq-consulting.de");
				contact.setLanguage("DE");
				contact.setIdDisplay("1234561");
				contact.setFacebookId("1234567890");
				contact.setBirthday(new Date(3000000000L));
				contact.setVisitPage(new Timestamp(System.currentTimeMillis() - 3000000L));
				contact.setGender((short) 1);
				contact.setType(ContactType.admin);
				contact.setTimezone(TimeZone.getDefault().getID());
				contact.setPseudonym("pseudonym");
				contact.setVerified(true);
				contact.setPassword(Encryption.encryptDB("secret_password"));
				contact.setPasswordReset(System.currentTimeMillis());
				repository.save(contact);
			} while (contact.getId().compareTo(id) < 0);
		}
		return contact;
	}

	public void setEventDate(final BigInteger id, final Timestamp date) throws Exception {
		repository.executeUpdate("update Event set startDate=?1, endDate=?2, contactId=1 where id=" + id, date, date);
		repository.executeUpdate("update EventParticipate set eventDate=?1 where eventId=" + id, date);
		repository.executeUpdate("update Contact set latitude=2, longitude=-2 where id=1");
	}

	public void initPaypalSandbox() throws Exception {
		String v = "";
		for (int i = 1; i < 20; i++)
			v += "," + i;
		final Setting setting = new Setting();
		setting.setLabel("paypal.sandbox");
		setting.setData(v.substring(1));
		repository.save(setting);
	}
}
