package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactWhatToDo;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Service
public class WhatToDoService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	public void findAndNotify() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listWhat2do);
		params.setSearch("contactWhatToDo.active=true");
		params.setLimit(0);
		final Result ids = repository.list(params);
		params.setQuery(Query.contact_list);
		params.setDistance(50);
		for (int i = 0; i < ids.size(); i++) {
			final ContactWhatToDo contactWhatToDo = repository.one(ContactWhatToDo.class,
					(BigInteger) ids.get(i).get("contactWhatToDo.id"));
			if (contactWhatToDo.getTime().before(new Date(Instant.now().minus(Duration.ofHours(1)).toEpochMilli()))) {
				contactWhatToDo.setActive(false);
				repository.save(contactWhatToDo);
			} else {
				final Contact contact = repository.one(Contact.class, contactWhatToDo.getContactId());
				String search = Score.getSearchContact(contact);
				if (search.length() > 0) {
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setUser(contact);
					search = "(" + search + ") and (";
					final String[] cats = contactWhatToDo.getKeywords().split(",");
					for (int i2 = 0; i2 < cats.length; i2++)
						search += "length(contact.attr" + cats[i2] + ")>0 or ";
					params.setSearch(search.substring(0, search.length() - 4) + ')');
					final Result result = repository.list(params);
					final ZonedDateTime t = Instant.ofEpochMilli(contactWhatToDo.getTime().getTime())
							.minus(Duration.ofMinutes(
									contact.getTimezoneOffset() == null ? -60
											: contact.getTimezoneOffset().longValue()))
							.atZone(ZoneOffset.UTC);
					final String time = t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute();
					for (int i2 = 0; i2 < result.size(); i2++) {
						final Contact contact2 = repository.one(Contact.class,
								(BigInteger) result.get(i2).get("contact.id"));
						if (Score.getContact(contact, contact2) > 0.5) {
							String verb = "";
							for (int i3 = 0; i3 < cats.length; i3++)
								verb += (i3 == 0 ? ""
										: i3 == cats.length - 1 ? Text.or.getText(contact.getLanguage()) : ", ") + Text
												.valueOf(Text.category_verb0.name().substring(0,
														Text.category_verb0.name().length() - 1) + cats[i3])
												.getText(contact2.getLanguage());
							notificationService.sendNotification(contact, contact2, NotificationID.wtd,
									Strings.encodeParam("p=" + contact.getId()), time, verb);
						}
					}
				}
			}
		}
	}
}
