package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ClientMarketingResult.PollResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Group;
import com.jq.findapp.service.CronService.Job;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class MarketingService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	@Job(cron = "* 9,10,11,12,13,14,15,16")
	public CronResult runEvent() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.misc_listClient);
		final Result list = repository.list(params);
		params.setQuery(Query.event_listId);
		for (int i = 0; i < list.size(); i++) {
			final Client client = repository.one(Client.class, (BigInteger) list.get(i).get("client.id"));
			final JsonNode node = Json.toNode(Attachment.resolve(client.getStorage()));
			if (node.has("marketing")) {
				int count = 0;
				for (int i2 = 0; i2 < node.get("marketing").size(); i2++) {
					if (Math.random() > 0.8) {
						final long contactId = node.get("marketing").get(i2).get("user").asLong();
						params.setSearch(
								"event.contactId=" + contactId
										+ " and event.startDate>=cast('" + Instant.now().minus(Duration.ofDays(2)).toString().substring(0, 19) + "' as timestamp) and event.type='Inquiry'");
						if (repository.list(params).size() == 0) {
							final JsonNode text = node.get("marketing").get(i2).get("text");
							final LocalDate date = LocalDate.now();
							final Event event = new Event();
							event.setContactId(BigInteger.valueOf(contactId));
							event.setDescription(text.get((int) (Math.random() * text.size() % text.size())).asText());
							event.setStartDate(new Timestamp(Instant.parse(date.getYear() + "-" + date.getMonthValue()
									+ "-" + date.getDayOfMonth() + "T" + (int) (16 + Math.random() * 5) + (":"
											+ ((int) (Math.random() * 4) % 4) * 15).replace(":0", ":00")
									+ ":00.00Z")
									.toEpochMilli()));
							event.setPublish(true);
							event.setType(EventType.Inquiry);
							repository.save(event);
							count++;
						}
					}
				}
				if (count > 0)
					result.body += client.getId() + ":" + count + "\n";
			}
		}
		return result;
	}

	@Job(group = Group.Two)
	public CronResult run() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
		params.setLimit(0);
		final String today = Instant.now().toString().substring(0, 19);
		params.setSearch("clientMarketing.share=true and clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
		final Result list = repository.list(params);
		params.setQuery(Query.contact_listNotificationId);
		try {
			for (int i = 0; i < list.size(); i++) {
				params.setSearch("m=" + list.get(i).get("clientMarketing.id"));
				params.getUser().setClientId((BigInteger) list.get(i).get("clientMarketing.clientId"));
				final Result contacts = repository.list(params);
				result.body += list.get(i).get("clientMarketing.id") + ": " + contacts.size() + "\n";
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) list.get(i).get("clientMarketing.id"));
				for (int i2 = 0; i2 < contacts.size(); i2++) {
					boolean run = true;
					final Contact contact = repository.one(Contact.class,
							(BigInteger) contacts.get(i2).get("contact.id"));
					if (Strings.isEmpty(contact.getVersion()) || "0.6.7".compareTo(contact.getVersion()) > 0)
						run = false;
					else if (!Strings.isEmpty(clientMarketing.getLanguage())
							&& !clientMarketing.getLanguage().contains(contact.getLanguage()))
						run = false;
					else if (!Strings.isEmpty(clientMarketing.getGender())
							&& !clientMarketing.getGender().contains("" + contact.getGender()))
						run = false;
					else if (!Strings.isEmpty(clientMarketing.getSkills())
							&& (contact.getSkills() == null ||
									!('|' + contact.getSkills() + '|')
											.contains('|' + clientMarketing.getSkills() + '|')))
						run = false;
					else if (!Strings.isEmpty(clientMarketing.getAge())
							&& (contact.getAge() == null
									|| Integer.valueOf(clientMarketing.getAge().split(",")[0]) > contact.getAge()
									|| Integer.valueOf(clientMarketing.getAge().split(",")[1]) < contact.getAge()))
						run = false;
					else if (!Strings.isEmpty(clientMarketing.getRegion())) {
						final QueryParams params2 = new QueryParams(Query.contact_listGeoLocationHistory);
						params2.setSearch("contactGeoLocationHistory.contactId=" + contact.getId());
						final Result result2 = repository.list(params2);
						if (result2.size() > 0) {
							final GeoLocation geoLocation = repository.one(GeoLocation.class,
									(BigInteger) result2.get(0).get("contactGeoLocationHistory.geoLocationId"));
							final String region = " " + clientMarketing.getRegion() + " ";
							if (!region.contains(" " + geoLocation.getCountry() + " ") &&
									!region.toLowerCase().contains(" " + geoLocation.getTown().toLowerCase() + " ")) {
								String s = geoLocation.getZipCode();
								boolean zip = false;
								while (s.length() > 1 && !zip) {
									zip = region.contains(" " + geoLocation.getCountry() + "-" + s + " ");
									s = s.substring(0, s.length() - 1);
								}
								if (!zip)
									run = false;
							}
						} else
							run = false;
					}
					if (run) {
						final Poll poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
						notificationService.sendNotification(null, contact,
								poll.textId, "m=" + clientMarketing.getId(), poll.subject);
					}
				}
				publish(clientMarketing, false);
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	@Job(group = Group.Two)
	public CronResult runResult() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.published=false and clientMarketing.endDate<=cast('" + Instant.now()
					+ "' as timestamp)");
			params.setLimit(0);
			final Result clientMarketings = repository.list(params);
			for (int i = 0; i < clientMarketings.size(); i++) {
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) clientMarketings.get(i).get("clientMarketing.id"));
				synchronizeResult(clientMarketing.getId());
				params.setQuery(Query.contact_listMarketing);
				params.setSearch(
						"contactMarketing.finished=true and contactMarketing.contactId is not null and contactMarketing.clientMarketingId="
								+ clientMarketing.getId());
				final Result users = repository.list(params);
				final Poll poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
				final List<Object> sent = new ArrayList<>();
				final String field = "contactMarketing.contactId";
				for (int i2 = 0; i2 < users.size(); i2++) {
					if (!sent.contains(users.get(i2).get(field))) {
						final Contact contact = repository.one(Contact.class, (BigInteger) users.get(i2).get(field));
						notificationService.sendNotification(null, contact,
								TextId.notification_clientMarketingPollResult, "m=" + clientMarketing.getId(),
								text.getText(contact, poll.textId).replace("<jq:EXTRA_1 />", poll.subject));
						sent.add(users.get(i2).get(field));
					}
				}
				final ClientMarketingResult clientMarketingResult = repository.one(ClientMarketingResult.class,
						(BigInteger) clientMarketings.get(i).get("clientMarketingResult.id"));
				clientMarketingResult.setPublished(true);
				publish(clientMarketing, true);
				repository.save(clientMarketingResult);
				result.body = "sent " + sent.size() + " for " + clientMarketing.getId() + "\n";
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private void publish(final ClientMarketing clientMarketing, boolean result) {
		if (clientMarketing.getShare() && clientMarketing.getCreateResult()
				&& Strings.isEmpty(clientMarketing.getPublishId())) {
			final JsonNode clientJson = Json.toNode(Attachment
					.resolve(repository.one(Client.class, clientMarketing.getClientId()).getStorage()));
			final Poll poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()),
					Poll.class);
			final Contact contact = new Contact();
			contact.setLanguage("DE");
			contact.setClientId(clientMarketing.getClientId());
			String s = text
					.getText(contact, poll.textId == null ? TextId.notification_clientMarketingPoll : poll.textId)
					.replace("<jq:EXTRA_1 />", poll.subject)
					+ (Strings.isEmpty(poll.publishingPostfix) ? "" : poll.publishingPostfix)
					+ (clientJson.has("publishingPostfix")
							? "\n\n" + clientJson.get("publishingPostfix").asText()
							: "");
			if (result)
				s = text.getText(contact, TextId.notification_clientMarketingPollResult)
						.replace("{0}", s);
			final String fbId = externalService.publishOnFacebook(clientMarketing.getClientId(),
					s, "/rest/marketing/" + clientMarketing.getId() + (result ? "/result" : ""));
			if (fbId != null) {
				clientMarketing.setPublishId(fbId);
				repository.save(clientMarketing);
			}
		}
	}

	public synchronized ClientMarketingResult synchronizeResult(final BigInteger clientMarketingId) {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		final Poll poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
		if (!clientMarketing.getCreateResult())
			return null;
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketingId);
		params.setLimit(0);
		Result result = repository.list(params);
		final ClientMarketingResult clientMarketingResult;
		if (result.size() == 0) {
			clientMarketingResult = new ClientMarketingResult();
			clientMarketingResult.setClientMarketingId(clientMarketingId);
		} else
			clientMarketingResult = repository.one(ClientMarketingResult.class,
					(BigInteger) result.get(0).get("clientMarketingResult.id"));
		params.setQuery(Query.contact_listMarketing);
		params.setSearch("contactMarketing.clientMarketingId=" + clientMarketingId);
		result = repository.list(params);
		final PollResult pollResult = new PollResult();
		pollResult.participants = result.size();
		pollResult.finished = 0;
		for (int i2 = 0; i2 < result.size(); i2++) {
			final String answersText = (String) result.get(i2).get("contactMarketing.storage");
			if (answersText != null && answersText.length() > 2) {
				pollResult.finished++;
				final JsonNode contactAnswer = Json.toNode(answersText);
				final Iterator<String> it = contactAnswer.fieldNames();
				while (it.hasNext()) {
					final String key = it.next();
					if (Integer.valueOf(key.substring(1)) < poll.questions.size()) {
						final Question question = poll.questions.get(Integer.valueOf(key.substring(1)));
						if (!pollResult.answers.containsKey(key)) {
							pollResult.answers.put(key, new HashMap<>());
							final List<Integer> a = new ArrayList<>();
							for (int i3 = 0; i3 < question.answers.size(); i3++)
								a.add(0);
							pollResult.answers.get(key).put("a", a);
						}
						for (int i = 0; i < contactAnswer.get(key).get("a").size(); i++) {
							final int index = contactAnswer.get(key).get("a").get(i).asInt();
							@SuppressWarnings("unchecked")
							final List<Integer> totalAnswer = (List<Integer>) pollResult.answers.get(key).get("a");
							totalAnswer.set(index, totalAnswer.get(index) + 1);
						}
						if (contactAnswer.get(key).has("t")) {
							final Map<String, Object> o = pollResult.answers.get(key);
							if (!o.containsKey("t"))
								o.put("t", "");
							o.put("t", o.get("t") + "<div>" + contactAnswer.get(key).get("t").asText() + "</div>");
						}
					}
				}
			}
		}
		clientMarketingResult.setStorage(Json.toString(pollResult));
		repository.save(clientMarketingResult);
		return clientMarketingResult;
	}
}
