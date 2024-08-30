package com.jq.findapp.service.backend;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
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
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ClientMarketingResult.PollResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.Device;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.AuthenticationService;
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
	private AuthenticationService authenticationService;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	public SchedulerResult run() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
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
						final ObjectMapper om = new ObjectMapper();
						om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
						final Poll poll = om.readValue(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
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

	public SchedulerResult runResult() {
		final SchedulerResult result = new SchedulerResult();
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
				final ObjectMapper om = new ObjectMapper();
				om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				final Poll poll = om.readValue(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
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
				result.body = "sent " + sent.size() + " for " + clientMarketing.getId() + "\n";
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private void publish(final ClientMarketing clientMarketing, boolean result) throws Exception {
		if (clientMarketing.getShare() && clientMarketing.getCreateResult()
				&& Strings.isEmpty(clientMarketing.getPublishId())) {
			final ObjectMapper om = new ObjectMapper();
			om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			final JsonNode clientJson = om.readTree(Attachment
					.resolve(repository.one(Client.class, clientMarketing.getClientId()).getStorage()));
			final Poll poll = om.readValue(Attachment.resolve(clientMarketing.getStorage()),
					Poll.class);
			final Contact contact = new Contact();
			contact.setLanguage("DE");
			contact.setClientId(clientMarketing.getClientId());
			String s = text
					.getText(contact, poll.textId == null ? TextId.notification_clientMarketingPoll : poll.textId)
					.replace("{0}", poll.subject)
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

	public SchedulerResult runSent() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setLimit(0);
		return result;
	}

	public SchedulerResult runUnfinished() {
		final SchedulerResult result = new SchedulerResult();
		final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		final Map<BigInteger, String> htmls = new HashMap<>();
		final QueryParams params = new QueryParams(Query.contact_listMarketing);
		params.setLimit(0);
		params.setSearch("contactMarketing.finished=false and contactMarketing.createdAt>cast('"
				+ Instant.now().minus(Duration.ofDays(14)).toString()
				+ "' as timestamp) and contactMarketing.createdAt<cast('" +
				Instant.now().minus(Duration.ofHours(6)).toString() + "' as timestamp)");
		try {
			final Result list = repository.list(params);
			final long end = Instant.now().plus(Duration.ofDays(1)).toEpochMilli();
			final ObjectMapper om = new ObjectMapper();
			om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			params.setQuery(Query.misc_listTicket);
			for (int i = 0; i < list.size(); i++) {
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) list.get(i).get("contactMarketing.clientMarketingId"));
				if (clientMarketing.getEndDate().getTime() > end) {
					int count = 0;
					final JsonNode answer = om.readTree((String) list.get(i).get("contactMarketing.storage"));
					if (answer.has("locationId")) {
						final Poll poll = om.readValue((String) list.get(i).get("clientMarketing.storage"), Poll.class);
						final Location location = repository.one(Location.class,
								new BigInteger(answer.get("locationId").asText()));
						if (!Strings.isEmpty(location.getSecret())) {
							params.setSearch("ticket.type='EMAIL' and ticket.subject='" + location.getEmail() + "'");
							final Result emails = repository.list(params);
							boolean sent = false;
							final Contact contact = new Contact();
							contact.setLanguage("DE");
							contact.setClientId(clientMarketing.getClientId());
							final String subject = text.getText(contact,
									TextId.valueOf("marketing_" + poll.locationPrefix + "SubjectPrefix"))
									+ "Vervollständigung Deiner Location Daten...";
							for (int i2 = 0; i2 < emails.size(); i2++) {
								if (((String) emails.get(i2).get("ticket.note")).startsWith(subject)) {
									sent = true;
									break;
								}
							}
							if (!sent) {
								final Client client = repository.one(Client.class, clientMarketing.getClientId());
								final String url = client.getUrl() + "/?m=" + clientMarketing.getId() + "&i="
										+ location.getId() + "&h=" + location.getSecret().hashCode();
								final String date = df.format(list.get(i).get("contactMarketing.modifiedAt") == null
										? list.get(i).get("contactMarketing.createdAt")
										: list.get(i).get("contactMarketing.modifiedAt"));
								if (!htmls.containsKey(client.getId()))
									htmls.put(client.getId(), createHtmlTemplate(client));
								final String s = text
										.getText(contact,
												TextId.valueOf("marketing_" + poll.locationPrefix + "TextUnfinished"))
										.replace("{date}", date).replace("{location}", location.getName())
										+ text.getText(contact,
												TextId.valueOf("marketing_" + poll.locationPrefix + "Postfix"));
								notificationService.sendEmail(client, null, location.getEmail(),
										subject, s.replace("{url}", url),
										htmls.get(client.getId()).replace("<jq:text />",
												s.replace("\n", "<br/>").replace("{url}",
														"<a href=\"" + url + "\">" + client.getUrl() + "</a>")));
								count++;
							}
						}
					}
					if (count > 0)
						result.body += clientMarketing.getId() + ": " + count + "\n";
				}
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	public SchedulerResult runLocation() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setLimit(0);
		final ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		try {
			final String today = Instant.now().toString().substring(0, 19);
			params.setSearch("clientMarketing.startDate<=cast('" + today
					+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
			final Result list = repository.list(params);
			params.setQuery(Query.location_listId);
			for (int i = 0; i < list.size(); i++) {
				final Poll poll = om.readValue((String) list.get(i).get("clientMarketing.storage"), Poll.class);
				if (!Strings.isEmpty(poll.locationPrefix)) {
					final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
							(BigInteger) list.get(i).get("clientMarketing.id"));
					final Client client = repository.one(Client.class, clientMarketing.getClientId());
					final String html = createHtmlTemplate(client);
					params.setSearch(
							"location.email like '%@%' and (length(location.marketingMail)=0 or cast(REGEXP_LIKE('"
									+ clientMarketing.getId() + "',location.marketingMail) as integer)=0)"
									+ (Strings.isEmpty(poll.locationSearch) ? "" : " and " + poll.locationSearch));
					final Result locations = repository.list(params);
					params.setQuery(Query.misc_listTicket);
					int count = 0, countSent = 0;
					for (int i2 = 0; i2 < locations.size(); i2++) {
						final Location location = repository.one(Location.class,
								(BigInteger) locations.get(i2).get("location.id"));
						params.setSearch("ticket.type='EMAIL' and ticket.subject='" + location.getEmail() + "'");
						final Result emails = repository.list(params);
						boolean sent = false;
						final Contact contact = new Contact();
						contact.setLanguage("DE");
						contact.setClientId(clientMarketing.getClientId());
						final String subject = text.getText(contact,
								TextId.valueOf("marketing_" + poll.locationPrefix + "SubjectPrefix"))
								+ "möchtest Du mehr Gäste?";
						for (int i3 = 0; i3 < emails.size(); i3++) {
							if (((String) emails.get(i3).get("ticket.note")).startsWith(subject)) {
								sent = true;
								countSent++;
								break;
							}
						}
						if (!sent) {
							if (location.getSecret() == null) {
								location.setSecret(Strings.generatePin(64));
								repository.save(location);
							}
							final String url = client.getUrl() + "/?m=" + clientMarketing.getId() + "&i="
									+ location.getId() + "&h=" + location.getSecret().hashCode();
							final String body = text.getText(contact,
									TextId.valueOf("marketing_" + poll.locationPrefix + "Text"))
									+ text.getText(contact,
											TextId.valueOf("marketing_" + poll.locationPrefix + "Postfix"));
							notificationService.sendEmail(client, null, "mani.afschar@jq-consulting.de", // location.getEmail(),
									subject, body.replace("{url}", url),
									html.replace("<jq:text />",
											body.replace("\n", "<br/>").replace("{url}",
													"<a href=\"" + url + "\">" + client.getUrl() + "</a>")));
							location.setMarketingMail(
									(Strings.isEmpty(location.getMarketingMail()) ? ""
											: location.getMarketingMail() + "|")
											+ clientMarketing.getId());
							// repository.save(location);
							if (++count >= 10)
								break;
						}
					}
					result.body += clientMarketing.getId() + ": " + count + " - " + locations.size() + " - " + countSent
							+ "\n";
				}
			}

		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	String createHtmlTemplate(Client client) throws IOException {
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
		return html;
	}

	public String locationUpdate(final ContactMarketing contactMarketing) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
				contactMarketing.getClientMarketingId());
		final ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		final JsonNode answers = om.readTree(Attachment.resolve(contactMarketing.getStorage()));
		if (answers.has("locationId")) {
			final Location location = repository.one(Location.class,
					new BigInteger(answers.get("locationId").asText()));
			if ((location.getUpdatedAt() == null || location.getUpdatedAt().before(clientMarketing.getStartDate()))
					&& location.getSecret().hashCode() == answers.get("hash").asInt()) {
				final Poll poll = om.readValue(Attachment.resolve(
						clientMarketing.getStorage()), Poll.class);
				String result = "<ul><li>Deine Location wurde erfolgreich akualisiert.</li>";
				String email = "Lieben Dank für Deine Teilnahme, Deine Location wurde erfolgreich akualisiert:\n\n";
				location.setUpdatedAt(new Timestamp(Instant.now().toEpochMilli()));
				location.setSkills(null);
				for (int i = 0; i < poll.questions.size(); i++) {
					if (answers.get("q" + i).has("t")) {
						String s = answers.get("q" + i).get("t").asText();
						if (!Strings.isEmpty(s)) {
							if ("name".equals(poll.questions.get(i).id) && !Strings.isEmpty(s)) {
								location.setName(s);
								email += s + "\n";
							} else if ("address".equals(poll.questions.get(i).id) && s.contains("\n")
									&& !Strings.isEmpty(s)) {
								location.setAddress(s);
								email += s + "\n";
							} else if ("telephone".equals(poll.questions.get(i).id)) {
								location.setTelephone(s);
								if (!Strings.isEmpty(s))
									email += s + "\n";
							} else if ("url".equals(poll.questions.get(i).id)) {
								location.setUrl(s);
								email += s + "\n";
							} else if ("description".equals(poll.questions.get(i).id)) {
								location.setDescription(s);
								if (!Strings.isEmpty(s))
									email += "\n" + s + "\n\n";
							} else if (poll.questions.get(i).id != null
									&& poll.questions.get(i).id.startsWith("skills"))
								location.setSkillsText(s);
							else if ("feedback".equals(poll.questions.get(i).id) && !Strings.isEmpty(s)) {
								result += "<li>Lieben Dank für Dein Feedback.</li>";
								email += "Dein Feedback:\n" + s + "\n\n";
							}
						}
					}
					if (answers.get("q" + i).has("a")) {
						String s = "";
						for (int i2 = 0; i2 < answers.get("q" + i).get("a").size(); i2++) {
							final int index = answers.get("q" + i).get("a").get(i2).asInt();
							s += "|" + (poll.questions.get(i).answers.get(index).key == null
									? poll.questions.get(i).answers.get(index).answer
									: poll.questions.get(i).answers.get(index).key);
						}
						if (!Strings.isEmpty(s)) {
							if (poll.questions.get(i).id != null && poll.questions.get(i).id.startsWith("skills")) {
								s = (Strings.isEmpty(location.getSkills()) ? "" : location.getSkills() + "|")
										+ s.replace("|0", "").substring(1);
								location.setSkills(s);
							} else if ("cards".equals(poll.questions.get(i).id) && !"|0".equals(s)) {
								result += "<li>Marketing-Material senden wir Dir an die Adresse Deiner Location.</li>";
								email += "Marketing-Material senden wir Dir an die Adresse Deiner Location.\n\n";
							} else if ("account".equals(poll.questions.get(i).id) && "|1".equals(s)) {
								final InternalRegistration registration = new InternalRegistration();
								registration.setAgb(true);
								registration.setClientId(clientMarketing.getClientId());
								registration.setDevice(Device.computer);
								registration.setEmail(location.getEmail());
								registration.setLanguage("DE");
								registration.setOs(OS.web);
								registration.setPseudonym(
										location.getEmail().substring(0, location.getEmail().indexOf('@')));
								registration.setTime(6000);
								registration.setTimezone("Europe/Berlin");
								registration.setVersion("0.7.1");
								try {
									location.setContactId(authenticationService.register(registration).getId());
									result += "<li>Ein Zugang wurde für Dich angelegt, eine Email versendet.</li>";
								} catch (Exception ex) {
									result += "<li>Ein Zugang konnte nicht angelegt werden, die Email ist bereits registriert! Versuche Dich anzumelden oder über den \"Passwort vergessen\" Dialog Dir Dein Passwort zurücksetzen zu lassen.</li>";
									email += "Ein Zugang konnte nicht angelegt werden, Deine Email ist bereits registriert! Versuche Dich anzumelden oder über den \"Passwort vergessen\" Dialog Dir Dein Passwort zurücksetzen zu lassen.\n\n";
								}
							} else if ("cooperation".equals(poll.questions.get(i).id) && "|1".equals(s)) {
								result += "<li>Wir freuen uns auf eine weitere Zusammenarbeit und melden uns in Bälde bei Dir.</li>";
								email += "Wir freuen uns auf eine weitere Zusammenarbeit und melden uns in Bälde bei Dir.\n\n";
							}
						}
					}
				}
				location.setSecret(null);
				repository.save(location);
				final Client client = repository.one(Client.class, clientMarketing.getClientId());
				email += "\n\n\n" + client.getUrl() + "?" + Strings.encodeParam("l=" + location.getId());
				notificationService.sendEmail(client, null,
						location.getEmail(),
						"Deine Location " + location.getName(), email,
						createHtmlTemplate(repository.one(Client.class, clientMarketing.getClientId()))
								.replace("<jq:text />", email.replace("\n", "<br/>")));
				return result + "</ul>";
			}
		}
		return null;
	}

	public synchronized ClientMarketingResult synchronizeResult(final BigInteger clientMarketingId) throws Exception {
		final ObjectMapper om = new ObjectMapper();
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		final Poll poll = om.readValue(Attachment.resolve(clientMarketing.getStorage()), Poll.class);
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
				final JsonNode contactAnswer = om.readTree(answersText);
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
		clientMarketingResult.setStorage(om.writeValueAsString(pollResult));
		repository.save(clientMarketingResult);
		return clientMarketingResult;
	}
}
