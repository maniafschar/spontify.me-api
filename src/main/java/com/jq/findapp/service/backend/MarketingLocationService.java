package com.jq.findapp.service.backend;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.api.model.InternalRegistration;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.Device;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Event.Repetition;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class MarketingLocationService {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	@FunctionalInterface
	private interface Decide<X, Y, Z> {
		public boolean apply(X x, Y y, Z z);
	}

	@FunctionalInterface
	private interface Url<X, Y, Z> {
		public String apply(X x, Y y, Z z);
	}

	public SchedulerResult run() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setLimit(0);
		try {
			final String today = Instant.now().toString().substring(0, 19);
			params.setSearch("clientMarketing.startDate<=cast('" + today
					+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
			final Result list = repository.list(params);
			params.setQuery(Query.location_listId);
			for (int i = 0; i < list.size(); i++) {
				final Poll poll = Json.toObject((String) list.get(i).get("clientMarketing.storage"), Poll.class);
				if ("updateLocation".equals(poll.type)) {
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
					int count = 0;
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
							notificationService.sendEmail(client, null, location.getEmail(),
									subject, body.replace("{url}", url),
									html.replace("<jq:text />",
											body.replace("\n", "<br/>").replace("{url}",
													"<a href=\"" + url + "\">" + client.getUrl() + "</a>")));
							location.setMarketingMail(
									(Strings.isEmpty(location.getMarketingMail()) ? ""
											: location.getMarketingMail() + "|")
											+ clientMarketing.getId());
							repository.save(location);
							if (++count >= 20)
								break;
						}
					}
					result.body += clientMarketing.getId() + ": " + count + "\n";
				}
			}

		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	public SchedulerResult runUnfinished() {
		return sendEmails("contactMarketing.finished=false and contactMarketing.createdAt>cast('"
				+ Instant.now().minus(Duration.ofDays(14)).toString()
				+ "' as timestamp) and contactMarketing.createdAt<cast('" +
				Instant.now().minus(Duration.ofHours(6)).toString() + "' as timestamp)",
				"Vervollständigung Deiner Location Daten...", "Unfinished",
				(location, poll, answer) -> !Strings.isEmpty(location.getSecret()),
				(url, clientMarketingId, location) -> url + "/?m=" + clientMarketingId + "&i="
						+ location.getId() + "&h=" + location.getSecret().hashCode());
	}

	public SchedulerResult runSent() {
		return sendEmails(
				"contactMarketing.createdAt<cast('" + Instant.parse("2024-09-04T05:00:00.00Z").toString()
						+ "' as timestamp)",
				"Deine Marketing-Sticker wurden heute versendet", "Sent",
				(location, poll, answer) -> {
					for (int i = 0; i < poll.questions.size(); i++) {
						if ("cards".equals(poll.questions.get(i).id)) {
							if (!answer.has("q" + i) || answer.get("q" + i).get("a").size() == 0)
								return false;
							final int index = answer.get("q" + i).get("a").get(0).asInt();
							return Integer.valueOf(poll.questions.get(i).answers.get(index).key) > 0;
						}
					}
					return false;
				},
				(url, clientMarketingId, location) -> url + (Strings.isEmpty(location.getSecret()) ? ""
						: "/?i=" + location.getId() + "&h=" + location.getSecret().hashCode()));
	}

	public SchedulerResult runCooperation() {
		return sendEmails(
				"1=1",
				"Dauerhaft mehr Gäste durch Fanclub!", "Cooperation",
				(location, poll, answer) -> {
					boolean cards = false, cooperation = false;
					for (int i = 0; i < poll.questions.size(); i++) {
						if ("cards".equals(poll.questions.get(i).id)) {
							if (answer.has("q" + i) && answer.get("q" + i).get("a").size() > 0) {
								final int index = answer.get("q" + i).get("a").get(0).asInt();
								cards = Integer.valueOf(poll.questions.get(i).answers.get(index).key) > 0;
							}
						} else if ("cooperation".equals(poll.questions.get(i).id)) {
							if (answer.has("q" + i) && answer.get("q" + i).get("a").size() > 0) {
								final int index = answer.get("q" + i).get("a").get(0).asInt();
								cooperation = Integer.valueOf(poll.questions.get(i).answers.get(index).key) > 0;
							}
						}
					}
					return cards || cooperation;
				},
				(url, clientMarketingId, location) -> {
					if (Strings.isEmpty(location.getSecret())) {
						location.setSecret(Strings.generatePin(64));
						repository.save(location);
					}
					return url + "/?m=" + clientMarketingId + "&i=" + location.getId() + "&h="
							+ location.getSecret().hashCode();
				});
	}

	private SchedulerResult sendEmails(final String search, final String subject, final String postfixText,
			final Decide<Location, Poll, JsonNode> decide, final Url<String, BigInteger, Location> url) {
		final SchedulerResult result = new SchedulerResult();
		final SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
		final Map<BigInteger, String> htmls = new HashMap<>();
		final QueryParams params = new QueryParams(Query.contact_listMarketing);
		params.setLimit(0);
		params.setSearch(search + " and (contactMarketing.status is null or contactMarketing.status not like '%"
				+ postfixText + "%')");
		try {
			final Result contactMarketings = repository.list(params);
			final long end = Instant.now().plus(Duration.ofDays(1)).toEpochMilli();
			final Map<BigInteger, Integer> counts = new HashMap<>();
			for (int i = 0; i < contactMarketings.size(); i++) {
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) contactMarketings.get(i).get("contactMarketing.clientMarketingId"));
				final JsonNode answer = Json.toNode((String) contactMarketings.get(i).get("contactMarketing.storage"));
				if (answer.has("locationId") && clientMarketing.getEndDate().getTime() > end) {
					final Poll poll = Json.toObject(Attachment.resolve(clientMarketing.getStorage()),
							Poll.class);
					final Location location = repository.one(Location.class,
							new BigInteger(answer.get("locationId").asText()));
					if (decide.apply(location, poll, answer)) {
						final Contact contact = new Contact();
						contact.setLanguage("DE");
						contact.setClientId(clientMarketing.getClientId());
						final String subject2 = text.getText(contact,
								TextId.valueOf("marketing_" + poll.locationPrefix + "SubjectPrefix")) + subject;
						final ContactMarketing contactMarketing = repository.one(ContactMarketing.class,
								(BigInteger) contactMarketings.get(i).get("contactMarketing.id"));
						final Client client = repository.one(Client.class, clientMarketing.getClientId());
						final String u = url.apply(client.getUrl(), clientMarketing.getId(), location);
						final String date = df
								.format(contactMarketing.getModifiedAt() == null
										? contactMarketing.getCreatedAt()
										: contactMarketing.getModifiedAt());
						if (!htmls.containsKey(client.getId()))
							htmls.put(client.getId(), createHtmlTemplate(client));
						final String body = text
								.getText(contact,
										TextId.valueOf(
												"marketing_" + poll.locationPrefix + postfixText))
								.replace("{date}", date).replace("{location}", location.getName())
								+ text.getText(contact,
										TextId.valueOf("marketing_" + poll.locationPrefix + "Postfix"));
						notificationService.sendEmail(client, null, location.getEmail(),
								subject2, body.replace("{url}", u),
								htmls.get(client.getId()).replace("<jq:text />",
										body.replace("\n", "<br/>").replace("{url}",
												"<a href=\"" + u + "\">" + client.getUrl() + "</a>")));
						contactMarketing.setStatus((Strings.isEmpty(contactMarketing.getStatus()) ? ""
								: contactMarketing.getStatus() + "|") + postfixText);
						repository.save(contactMarketing);
						if (!counts.containsKey(clientMarketing.getId()))
							counts.put(clientMarketing.getId(), 0);
						counts.put(clientMarketing.getId(), counts.get(clientMarketing.getId()) + 1);
						result.body = counts.toString();
					}
				}
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	String createHtmlTemplate(Client client) {
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
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
		final int a = html.indexOf("</a>");
		html = html.substring(0, a + 4) + html.substring(html.indexOf("<jq:text"));
		html = html.substring(0, html.lastIndexOf("<div>", a)) + html.substring(a);
		final JsonNode css = Json.toNode(Attachment.resolve(client.getStorage())).get("css");
		final Iterator<String> it = css.fieldNames();
		while (it.hasNext()) {
			final String key = it.next();
			html = html.replace("--" + key, css.get(key).asText());
		}
		return html;
	}

	public String pollFinished(final ContactMarketing contactMarketing) {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
				contactMarketing.getClientMarketingId());
		final Poll poll = Json.toObject(Attachment.resolve(
				clientMarketing.getStorage()), Poll.class);
		final JsonNode answers = Json.toNode(Attachment.resolve(contactMarketing.getStorage()));
		if (answers.has("locationId") &&
				("updateLocation".equals(poll.type) || "createEvents".equals(poll.type))) {
			final Location location = repository.one(Location.class,
					new BigInteger(answers.get("locationId").asText()));
			if ("updateLocation".equals(poll.type))
				return update(contactMarketing, clientMarketing, poll, answers, location);
			return createEvents(contactMarketing, clientMarketing, poll, answers, location);
		}
		return null;
	}

	private String createEvents(final ContactMarketing contactMarketing, final ClientMarketing clientMarketing,
			final Poll poll, final JsonNode answers, final Location location) {
		if (answers.get("q0").has("a")) {
			String s = null;
			if (location.getContactId() == null || location.getContactId().intValue() < 10) {
				try {
					location.setContactId(createUser(clientMarketing.getClientId(), location));
					repository.save(location);
					s = "Ein Benutzer wurde für Dich angelegt, eine Email zur Verifizierung zugesendet. Bestätige den Link in der Email und Deine Events sind online und für jeden sichtbar.";
				} catch (Exception ex) {
					notificationService.createTicket(TicketType.ERROR, "MarketingLocation",
							Strings.stackTraceToString(ex), clientMarketing.getClientId());
					if (location.getContactId() == null) {
						location.setContactId(clientMarketing.getClientId());
						repository.save(location);
					}
				}
			}
			String description = answers.get("q2").get("t").asText();
			if (Strings.isEmpty(description))
				description = "Wir lieben Fußball!";
			for (int i = 0; i < answers.get("q0").get("a").size(); i++) {
				final Event event = new Event();
				event.setContactId(location.getContactId());
				event.setLocationId(location.getId());
				event.setDescription(description);
				event.setPublish(true);
				if (answers.get("q1").has("t")) {
					try {
						event.setMaxParticipants(Short.parseShort(answers.get("q1").get("t").asText()));
					} catch (NumberFormatException ex) {
					}
				}
				event.setRepetition(Repetition.Games);
				final int index = answers.get("q0").get("a").get(i).asInt();
				event.setSkills(poll.questions.get(0).answers.get(index).key);
				event.setType(EventType.Location);
				repository.save(event);
			}
			return (s == null ? "Deine Events wurden angelegt." : s)
					+ "\n\nÜbrigens, Du musst nichts mehr einstellen, auch zukünftige Spiele werden als Event automatisch für Dich angelegt.";
		}
		return null;
	}

	private String update(final ContactMarketing contactMarketing, final ClientMarketing clientMarketing,
			final Poll poll, final JsonNode answers, final Location location) {
		if ((location.getUpdatedAt() == null || location.getUpdatedAt().before(clientMarketing.getStartDate()))
				&& location.getSecret().hashCode() == answers.get("hash").asInt()) {
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
							s = s.replace("|0", "");
							if (!Strings.isEmpty(s))
								location.setSkills(
										(Strings.isEmpty(location.getSkills()) ? "" : location.getSkills() + "|")
												+ s.substring(1));
						} else if ("cards".equals(poll.questions.get(i).id) && !"|0".equals(s)) {
							if (Strings.isEmpty(location.getSkills()))
								location.setSkills("x.1");
							else if (!location.getSkills().contains("x.1"))
								location.setSkills(location.getSkills() + "|x.1");
							result += "<li>Marketing-Material senden wir Dir an die Adresse Deiner Location.</li>";
							email += "Marketing-Material senden wir Dir an die Adresse Deiner Location.\n\n";
						} else if ("account".equals(poll.questions.get(i).id) && "|1".equals(s)) {
							try {
								location.setContactId(createUser(clientMarketing.getClientId(), location));
								result += "<li>Ein Zugang wurde für Dich angelegt, eine Email versendet.</li>";
								location.setSecret(null);
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
		return null;
	}

	private BigInteger createUser(final BigInteger clientId, final Location location) throws Exception {
		final InternalRegistration registration = new InternalRegistration();
		registration.setAgb(true);
		registration.setClientId(clientId);
		registration.setDevice(Device.computer);
		registration.setEmail(location.getEmail());
		registration.setLanguage("DE");
		registration.setOs(OS.web);
		registration.setPseudonym(
				location.getEmail().substring(0, location.getEmail().indexOf('@')));
		registration.setTime(6000);
		registration.setTimezone("Europe/Berlin");
		registration.setVersion("0.7.1");
		return authenticationService.register(registration).getId();
	}
}