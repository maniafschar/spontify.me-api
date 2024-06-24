package com.jq.findapp.service.backend;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ClientMarketingResult.PollResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
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

	public SchedulerResult notificationClientMarketing() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
		final String today = Instant.now().toString().substring(0, 19);
		params.setSearch("clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
		final Result list = repository.list(params);
		params.setQuery(Query.contact_listNotificationId);
		try {
			for (int i = 0; i < list.size(); i++) {
				params.setSearch("m=" + list.get(i).get("clientMarketing.id"));
				params.getUser().setClientId((BigInteger) list.get(i).get("clientMarketing.clientId"));
				final Result contacts = repository.list(params);
				result.result += list.get(i).get("clientMarketing.id") + ": " + contacts.size() + "\n";
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) list.get(i).get("clientMarketing.id"));
				for (int i2 = 0; i2 < contacts.size(); i2++) {
					boolean run = true;
					final Contact contact = repository.one(Contact.class,
							(BigInteger) contacts.get(i2).get("contact.id"));
					if ("0.6.7".compareTo(contact.getVersion()) > 0)
						run = false;
					if (!Strings.isEmpty(clientMarketing.getLanguage())
							&& !clientMarketing.getLanguage().contains(contact.getLanguage()))
						run = false;
					if (!Strings.isEmpty(clientMarketing.getGender())
							&& !clientMarketing.getGender().contains("" + contact.getGender()))
						run = false;
					if (!Strings.isEmpty(clientMarketing.getAge())
							&& (contact.getAge() == null
									|| Integer.valueOf(clientMarketing.getAge().split(",")[0]) > contact.getAge()
									|| Integer.valueOf(clientMarketing.getAge().split(",")[1]) < contact.getAge()))
						run = false;

					if (!Strings.isEmpty(clientMarketing.getRegion())) {
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
						final Poll poll = new ObjectMapper()
								.readValue(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
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

	public SchedulerResult notificationClientMarketingResult() {
		final SchedulerResult result = new SchedulerResult();
		try {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.published=false and clientMarketing.endDate<=cast('" + Instant.now()
					+ "' as timestamp)");
			final Result clientMarketings = repository.list(params);
			for (int i = 0; i < clientMarketings.size(); i++) {
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) clientMarketings.get(i).get("clientMarketing.id"));
				params.setQuery(Query.contact_listMarketing);
				params.setSearch(
						"contactMarketing.finished=true and contactMarketing.contactId is not null and contactMarketing.clientMarketingId="
								+ clientMarketing.getId());
				final Result users = repository.list(params);
				final Poll poll = new ObjectMapper().readValue(Attachment.resolve(clientMarketing.getStorage()),
						Poll.class);
				final List<Object> sent = new ArrayList<>();
				final String field = "contactMarketing.contactId";
				for (int i2 = 0; i2 < users.size(); i2++) {
					if (!sent.contains(users.get(i2).get(field))) {
						final Contact contact = repository.one(Contact.class, (BigInteger) users.get(i2).get(field));
						notificationService.sendNotification(null, contact,
								TextId.notification_clientMarketingPollResult, "m=" + clientMarketing.getId(),
								text.getText(contact, TextId.notification_clientMarketingPollResult).replace("{0}",
										text.getText(contact, poll.textId).replace("{0}", poll.subject)));
						sent.add(users.get(i2).get(field));
					}
				}
				final ClientMarketingResult clientMarketingResult = repository.one(ClientMarketingResult.class,
						(BigInteger) clientMarketings.get(i).get("clientMarketingResult.id"));
				clientMarketingResult.setPublished(true);
				publish(clientMarketing, true);
				repository.save(clientMarketingResult);
				result.result = "sent " + sent.size() + " for " + clientMarketing.getId() + "\n";
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private void publish(final ClientMarketing clientMarketing, boolean result) throws Exception {
		if (clientMarketing.getShare()) {
			final JsonNode clientJson = new ObjectMapper()
					.readTree(Attachment
							.resolve(repository.one(Client.class, clientMarketing.getClientId()).getStorage()));
			final Poll poll = new ObjectMapper().readValue(Attachment.resolve(clientMarketing.getStorage()),
					Poll.class);
			final Contact contact = new Contact();
			contact.setLanguage("DE");
			contact.setClientId(clientMarketing.getClientId());
			String s = text.getText(contact, poll.textId).replace("{0}", poll.subject)
					+ (Strings.isEmpty(poll.publishingPostfix) ? "" : poll.publishingPostfix)
					+ (clientJson.has("publishingPostfix")
							? "\n\n" + clientJson.get("publishingPostfix").asText()
							: "");
			if (result)
				s = text.getText(contact, TextId.notification_clientMarketingPollResult)
						.replace("{0}", s);
			externalService.publishOnFacebook(clientMarketing.getClientId(),
					s, "/rest/marketing/" + clientMarketing.getId() + (result ? "/result" : ""));
		}
	}

	public SchedulerResult notificationSportbars() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
		final String today = Instant.now().toString().substring(0, 19);
		params.setSearch("clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
		final Result list = repository.list(params);
		params.setQuery(Query.location_listId);
		final String text = "Lieber Sky Sportsbar Kunde,\n\n"
				+ "unsere Fußball Community ist auf der Suche nach den besten Locations, in denen sie Live-Übertragungen gemeinsam feiern können. "
				+ "Ist Deine Bar eine coole Location für Fußball-Fans?\n\nNähere Infos findest Du unter Deinen persönlichen Link:\n\n"
				+ "{url}"
				+ "\n\nWir freuen uns auf Dein Feedback\n"
				+ "Viele Grüße\n"
				+ "Mani Afschar Yazdi\n"
				+ "Geschäftsführer\n"
				+ "JNet Quality Consulting GmbH\n"
				+ "0172 6379434";
		try {
			for (int i = 0; i < list.size(); i++) {
				if (((String) list.get(i).get("clientMarketing.storage")).contains("Sky Kunde")) {
					final Client client = repository.one(Client.class,
							(BigInteger) list.get(i).get("clientMarketing.clientId"));
					String html;
					try (final InputStream inHtml = getClass().getResourceAsStream("/template/email.html")) {
						html = IOUtils.toString(inHtml, StandardCharsets.UTF_8)
								.replace("<jq:logo />", client.getUrl() + "/images/logo.png")
								.replace("<jq:pseudonym />", "")
								.replace("<jq:time />", Strings.formatDate(null, new Date(), "Europe/Berlin"))
								.replace("<jq:link />", "")
								.replace("<jq:url />", "")
								.replace("<jq:newsTitle />", "")
								.replace("<jq:image />", "");
					}
					final int a = html.indexOf("</a>");
					html = html.substring(0, a + 4) + html.substring(html.indexOf("<jq:text"));
					html = html.substring(0, html.lastIndexOf("<div>", a)) + html.substring(a);
					final JsonNode css = new ObjectMapper()
							.readTree(Attachment.resolve(client.getStorage()))
							.get("css");
					final Iterator<String> it = css.fieldNames();
					while (it.hasNext()) {
						final String key = it.next();
						html = html.replace("--" + key, css.get(key).asText());
					}
					params.setSearch(
							"location.email like '%@%' and location.skills like '%x.1%' and cast(REGEXP_LIKE(location.marketingMail,'x.1') as integer)=0");
					params.setLimit(1);
					final Result locations = repository.list(params);
					for (int i2 = 1; i2 < locations.size(); i2++) {
						final Location location = repository.one(Location.class,
								(BigInteger) locations.get(i2).get("location.id"));
						if (location.getSecret() == null) {
							location.setSecret(Strings.generatePin(64));
							repository.save(location);
						}
						final String url = client.getUrl() + "/?m=" + list.get(i).get("clientMarketing.id") + "&i="
								+ location.getId() + "&h=" + location.getSecret().hashCode();
						notificationService.sendEmail(client, "", "mani.afschar@jq-consulting.de"
						/* location.getEmail() */,
								"Sky Sport Events: möchtest Du mehr Gäste?", text.replace("{url}", url),
								html.replace("<jq:text />", text.replace("\n", "<br />").replace("{url}",
										"<a href=\"" + url + "\">" + client.getUrl() + "</a>")));
						location.setMarketingMail(
								(Strings.isEmpty(location.getMarketingMail()) ? "" : location.getMarketingMail() + "|")
										+ list.get(i).get("clientMarketing.id"));
						repository.save(location);
					}
				}
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	public String locationUpdate(final ContactMarketing contactMarketing) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
				contactMarketing.getClientMarketingId());
		final Poll poll = new ObjectMapper().readValue(Attachment.resolve(
				clientMarketing.getStorage()), Poll.class);
		final JsonNode answers = new ObjectMapper().readTree(Attachment.resolve(contactMarketing.getStorage()));
		if (answers.has("locationId")) {
			final Location location = repository.one(Location.class,
					new BigInteger(answers.get("locationId").asText()));
			if ((location.getUpdatedAt() == null || location.getUpdatedAt().before(clientMarketing.getStartDate()))
					&& location.getSecret().hashCode() == answers.get("hash").asInt()) {
				final String result = "Deine Location wurde erfolgreich akualisiert";
				location.historize();
				location.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
				String s = answers.get("q0").get("t").asText();
				if (!Strings.isEmpty(s))
					location.setName(s);
				s = answers.get("q1").get("t").asText();
				if (!Strings.isEmpty(s) && s.contains("\n"))
					location.setAddress(s);
				s = answers.get("q2").get("t").asText();
				if (!Strings.isEmpty(s))
					location.setTelephone(s);
				s = answers.get("q3").get("t").asText();
				if (!Strings.isEmpty(s) && s.startsWith("https://"))
					location.setUrl(s);
				s = answers.get("q4").get("t").asText();
				if (!Strings.isEmpty(s))
					location.setDescription(s);
				repository.save(location);
				return result;
			}
		}
		return null;
	}

	public synchronized ClientMarketingResult synchronizeResult(final BigInteger clientMarketingId) throws Exception {
		final ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		final Poll poll = om.readValue(Attachment.resolve(
				repository.one(ClientMarketing.class, clientMarketingId).getStorage()), Poll.class);
		if (!poll.createResult)
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
				final PollResult contactAnswer = om.readValue(answersText, PollResult.class);
				for (String key : contactAnswer.answers.keySet()) {
					if (Integer.valueOf(key.substring(1)) < poll.questions.size()) {
						final Question question = poll.questions.get(Integer.valueOf(key.substring(1)));
						if (!pollResult.answers.containsKey(key)) {
							pollResult.answers.put(key, new HashMap<>());
							final List<Integer> a = new ArrayList<>();
							for (int i3 = 0; i3 < question.answers.size(); i3++)
								a.add(0);
							pollResult.answers.get(key).put("a", a);
						}
						for (int i = 0; i < ((List<?>) contactAnswer.answers.get(key).get("a")).size(); i++) {
							final int index = (int) ((List<?>) contactAnswer.answers.get(key).get("a")).get(i);
							@SuppressWarnings("unchecked")
							final List<Integer> totalAnswer = (List<Integer>) pollResult.answers.get(key).get("a");
							totalAnswer.set(index, totalAnswer.get(index) + 1);
						}
						if (!Strings.isEmpty(contactAnswer.answers.get(key).containsKey("t"))) {
							final Map<String, Object> o = pollResult.answers.get(key);
							if (!o.containsKey("t"))
								o.put("t", "");
							o.put("t", o.get("t") + "<div>" + contactAnswer.answers.get(key).get("t") + "</div>");
						}
					}
				}
			}
		}
		clientMarketingResult.setStorage(om.writeValueAsString(pollResult));
		repository.save(clientMarketingResult);
		return clientMarketingResult;
	}
}
