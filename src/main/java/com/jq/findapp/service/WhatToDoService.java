package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Service
public class WhatToDoService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	public void findMatchingSpontis() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listWhat2do);
		params.setSearch("contactWhatToDo.active=true");
		params.setLimit(0);
		final Result ids = repository.list(params);
		for (int i = 0; i < ids.size(); i++) {
			final ContactWhatToDo contactWhatToDo = repository.one(ContactWhatToDo.class,
					(BigInteger) ids.get(i).get("contactWhatToDo.id"));
			if (contactWhatToDo.getTime().before(new Timestamp(Instant.now().toEpochMilli()))) {
				contactWhatToDo.setActive(false);
				repository.save(contactWhatToDo);
			} else
				findMatchingSpontis(contactWhatToDo);
		}
	}

	public void findMatchingSpontis(ContactWhatToDo contactWhatToDo) throws Exception {
		final Contact contact = repository.one(Contact.class, contactWhatToDo.getContactId());
		String search = Score.getSearchContact(contact);
		if (search.length() > 0) {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setDistance(50);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setUser(contact);
			search = "(" + search + ") and (";
			final String[] cats = contactWhatToDo.getKeywords().split(",");
			for (int i2 = 0; i2 < cats.length; i2++)
				search += "length(contact.attr" + cats[i2] + ")>0 or ";
			params.setSearch(search.substring(0, search.length() - 4) + ") and contact.id<>" + contact.getId());
			final Result result = repository.list(params);
			final ZonedDateTime t = Instant.ofEpochMilli(contactWhatToDo.getTime().getTime())
					.minus(Duration.ofMinutes(contact.getTimezoneOffset()))
					.atZone(ZoneOffset.UTC);
			final String time = t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute();
			for (int i2 = 0; i2 < result.size(); i2++)
				notifyOnScore(contact, repository.one(Contact.class,
						(BigInteger) result.get(i2).get("contact.id")), cats, time);
		}
	}

	private void notifyOnScore(Contact contact, Contact contact2, String[] cats, String time) throws Exception {
		if (Score.getContact(contact, contact2) > 0.5) {
			String verb = "";
			for (int i3 = 0; i3 < cats.length; i3++)
				verb += (i3 == 0 ? ""
						: i3 == cats.length - 1 ? Text.or.getText(contact.getLanguage()) : ", ") + Text
								.valueOf(Text.category_verb0.name().substring(0,
										Text.category_verb0.name().length() - 1) + cats[i3])
								.getText(contact2.getLanguage());
			notificationService.sendNotification(contact, contact2, ContactNotificationTextType.contactWhatToDo,
					Strings.encodeParam("p=" + contact.getId()), time, verb);
		}
	}
}
