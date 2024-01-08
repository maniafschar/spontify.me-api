package com.jq.findapp.api;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.Position;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVideoCall;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.IpService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Strings;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("marketing")
public class MarketingApi {
	private static final Map<BigInteger, String> INDEXES = new HashMap<>();

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	@PostMapping
	public BigInteger pollAnswerCreate(@RequestBody final WriteEntity entity,
			@RequestHeader final BigInteger clientId,
			@RequestHeader(required = false) final BigInteger user,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		final ContactMarketing contactMarketing = new ContactMarketing();
		contactMarketing.populate(entity.getValues());
		if (user == null) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setSearch("log.ip='" + IpService.sanatizeIp(ip)
					+ "' and log.createdAt>cast('" + Instant.now().minus(Duration.ofHours(6)).toString()
					+ "' as timestamp) and log.uri='/action/marketing' and log.status=200 and log.method='POST' and log.clientId="
					+ clientId);
			final Result list = repository.list(params);
			params.setQuery(Query.contact_listMarketing);
			for (int i = 0; i < list.size(); i++) {
				final Instant time = Instant.ofEpochMilli(((Timestamp) list.get(i).get("log.createdAt")).getTime());
				params.setSearch(
						"contactMarketing.createdAt>=cast('"
								+ time.minus(Duration.ofSeconds(5)).toString() +
								"' as timestamp) and contactMarketing.createdAt<cast('"
								+ time.plus(Duration.ofSeconds(30)).toString()
								+ "' as timestamp) and contactMarketing.clientMarketingId="
								+ contactMarketing.getClientMarketingId());
				final Result list2 = repository.list(params);
				if (list2.size() > 0)
					return (BigInteger) list2.get(0).get("contactMarketing.id");
			}
		} else {
			final QueryParams params = new QueryParams(Query.contact_listMarketing);
			params.setSearch("contactMarketing.finished=false and contactMarketing.contactId=" + user
					+ " and contactMarketing.clientMarketingId="
					+ contactMarketing.getClientMarketingId());
			final Result list = repository.list(params);
			if (list.size() > 0)
				return (BigInteger) list.get(0).get("contactMarketing.id");
			contactMarketing.setContactId(user);
		}
		repository.save(contactMarketing);
		return contactMarketing.getId();
	}

	@PutMapping
	public void pollAnswerSave(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger clientId)
			throws Exception {
		final ContactMarketing contactMarketing = repository.one(ContactMarketing.class, entity.getId());
		contactMarketing.populate(entity.getValues());
		repository.save(contactMarketing);
		if (contactMarketing.getFinished()) {
			final JsonNode questions = new ObjectMapper().readTree(
					Attachment.resolve(
							repository.one(ClientMarketing.class, contactMarketing.getClientMarketingId())
									.getStorage()));
			final JsonNode answers = new ObjectMapper().readTree(Attachment.resolve(contactMarketing.getStorage()));
			final JsonNode email = answers.get("q" + (questions.get("questions").size() - 1)).get("t");
			if (email != null && Strings.isEmail(email.asText())) {
				final Contact to = new Contact();
				to.setEmail(email.asText());
				to.setPseudonym(to.getEmail());
				to.setTimezone(Strings.TIME_OFFSET);
				to.setClientId(clientId);
				to.setLanguage("DE");
				final StringBuffer s = new StringBuffer("<div style=\"text-align:left;\">"
						+ questions.get("prolog").asText().replaceAll("\n", "<br/>"));
				s.append("<br/><br/>");
				for (int i = 0; i < questions.get("questions").size(); i++) {
					if (answers.has("q" + i)) {
						final JsonNode question = questions.get("questions").get(i);
						s.append(question.get("question").asText());
						s.append("<ul>");
						for (int i2 = 0; i2 < question.get("answers").size(); i2++) {
							boolean selected = false;
							for (int i3 = 0; i3 < answers.get("q" + i).get("a").size(); i3++) {
								if (answers.get("q" + i).get("a").get(i3).intValue() == i2) {
									selected = true;
									break;
								}
							}
							s.append("<li style=\"list-style-type:" + (selected ? "initial" : "circle") + ";\">");
							if (selected)
								s.append("<b>");
							s.append(question.get("answers").get(i2).get("answer").asText());
							if (selected)
								s.append("</b>");
							s.append("</li>");
						}
						s.append("</ul>");
						if (answers.get("q" + i).has("t"))
							s.append(answers.get("q" + i).get("t").asText());
						s.append("<br/><br/>");
					}
				}
				s.append(questions.get("epilog").asText().replaceAll("\n", "<br/>") + "</div>");
				notificationService.sendNotificationEmail(null, to, s.toString(), null);
			}
		}
	}

	@GetMapping
	public List<Object[]> poll(@RequestHeader final BigInteger clientId,
			@RequestHeader(required = false) final BigInteger user,
			@RequestParam(name = "m", required = false) final BigInteger clientMarketingId) throws Exception {
		if (clientMarketingId != null) {
			final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
			if (clientMarketing != null && clientId.equals(clientMarketing.getClientId())) {
				if (clientMarketing.getEndDate() != null
						&& clientMarketing.getEndDate().getTime() < Instant.now().getEpochSecond() * 1000) {
					final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
					params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
					return repository.list(params).getList();
				}
				if (user != null) {
					final QueryParams params = new QueryParams(Query.contact_listMarketing);
					params.setSearch("contactMarketing.finished=true and contactMarketing.contactId=" + user
							+ " and contactMarketing.clientMarketingId=" + clientMarketingId);
					if (repository.list(params).size() > 0)
						return null;
				}
				final QueryParams params = new QueryParams(Query.misc_listMarketing);
				params.setSearch("clientMarketing.id=" + clientMarketingId);
				return repository.list(params).getList();
			}
			return null;
		}
		if (user == null)
			return null;
		final Contact contact = repository.one(Contact.class, user);
		final QueryParams params = new QueryParams(Query.contact_listGeoLocationHistory);
		params.setSearch("contactGeoLocationHistory.contactId=" + contact.getId());
		final Result result = repository.list(params);
		final GeoLocation geoLocation;
		if (result.size() > 0)
			geoLocation = repository.one(GeoLocation.class,
					(BigInteger) result.get(0).get("contactGeoLocationHistory.geoLocationId"));
		else
			geoLocation = null;
		params.setQuery(Query.misc_listMarketing);
		final String today = Instant.now().toString().substring(0, 19);
		String s = "clientMarketing.clientId=" + contact.getClientId()
				+ " and clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today
				+ "' as timestamp) and clientMarketing.storage like '%" + Attachment.SEPARATOR + "%'";

		s += " and (clientMarketing.language is null or length(clientMarketing.language)=0 or clientMarketing.language='"
				+ contact.getLanguage() + "'";

		s += ") and (clientMarketing.gender is null or length(clientMarketing.gender)=0";
		if (contact.getGender() != null)
			s += " or clientMarketing.gender like '%" + contact.getGender() + "%'";

		s += ") and (clientMarketing.age is null or length(clientMarketing.age)=0 or ";
		if (contact.getAge() == null)
			s += "clientMarketing.age='18,99'";
		else
			s += "cast(substring(clientMarketing.age,1,2) as integer)<=" + contact.getAge()
					+ " and cast(substring(clientMarketing.age,4,2) as integer)>="
					+ contact.getAge();

		s += ") and (clientMarketing.region is null or length(clientMarketing.region)=0";
		if (geoLocation == null)
			s += ")";
		else {
			s += " or (clientMarketing.region like '%" + geoLocation.getTown()
					+ "%' or concat(' ',clientMarketing.region,' ') like '% " + geoLocation.getCountry() + " %' or ";
			final String s2 = geoLocation.getZipCode();
			if (s2 != null) {
				for (int i = 1; i <= s2.length(); i++) {
					s += "concat(' ',clientMarketing.region,' ') like '% " + geoLocation.getCountry() + "-"
							+ s2.substring(0, i) + " %' or ";
				}
			}
			s = s.substring(0, s.length() - 4) + "))";
		}
		params.setSearch(s);
		final List<Object[]> list = repository.list(params).getList();
		params.setQuery(Query.contact_listMarketing);
		return list.stream().filter(e -> {
			if (e[0] instanceof String)
				return true;
			params.setSearch(
					"contactMarketing.contactId=" + contact.getId() + " and contactMarketing.clientMarketingId="
							+ e[0] + " and contactMarketing.finished=true");
			return repository.list(params).size() == 0;
		}).toList();
	}

	@GetMapping(path = "{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String poll(@PathVariable final BigInteger id) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, id);
		if (clientMarketing == null)
			return "";
		final boolean pollTerminated = clientMarketing.getEndDate() != null
				&& clientMarketing.getEndDate().getTime() / 1000 < Instant.now().getEpochSecond();
		final String image;
		if (pollTerminated) {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
			final Result result = repository.list(params);
			if (result.size() == 0 || result.get(0).get("clientMarketingResult.image") == null)
				return "";
			image = result.get(0).get("clientMarketingResult.image").toString();
		} else
			image = Attachment.resolve(clientMarketing.getImage());
		return getHtml(repository.one(Client.class, clientMarketing.getClientId()),
				(pollTerminated ? "result/" : "init/") + id, image, "");
	}

	@GetMapping(path = "location/{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String location(@PathVariable final BigInteger id) throws Exception {
		final Location location = repository.one(Location.class, id);
		if (location == null)
			return "";
		return getHtml(repository.one(Client.class, BigInteger.ONE), "location/" + id,
				Attachment.resolve(location.getImage()),
				location.getName() + " " + location.getAddress() + " " + location.getDescription());
	}

	@GetMapping(path = "news/{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String news(@PathVariable final BigInteger id) throws Exception {
		final ClientNews news = repository.one(ClientNews.class, id);
		if (news == null)
			return "";
		return getHtml(repository.one(Client.class, news.getClientId()), "news/" + id,
				Attachment.resolve(news.getImage()), news.getDescription());
	}

	@GetMapping(path = "event/{id}", produces = MediaType.TEXT_HTML_VALUE)
	public String event(@PathVariable final BigInteger id) throws Exception {
		final Event event = repository.one(Event.class, id);
		if (event == null)
			return "";
		final Contact contact = repository.one(Contact.class, event.getContactId());
		final Location location = repository.one(Location.class, event.getLocationId());
		String image = event.getImage();
		if (image == null)
			image = location.getImage();
		if (image == null)
			image = contact.getImage();
		return getHtml(repository.one(Client.class, contact.getClientId()), "event/" + id,
				Attachment.resolve(image),
				event.getDescription() + " " + location.getAddress() + " " + location.getDescription());
	}

	private String getHtml(final Client client, final String path, final String image, String title)
			throws IOException {
		String s;
		synchronized (INDEXES) {
			if (!INDEXES.containsKey(client.getId()))
				try (final InputStream in = new URL(client.getUrl()).openStream()) {
					INDEXES.put(client.getId(), IOUtils.toString(in, StandardCharsets.UTF_8));
				}
			s = INDEXES.get(client.getId());
		}
		final String url = Strings.removeSubdomain(client.getUrl());
		s = s.replaceFirst("<meta property=\"og:url\" content=\"([^\"].*)\"",
				"<meta property=\"og:url\" content=\"" + url + "/rest/action/marketing/" + path + '"');
		s = s.replaceFirst("<link rel=\"canonical\" href=\"([^\"].*)\"",
				"<link rel=\"canonical\" href=\"" + url + "/rest/action/marketing/" + path + '"');
		s = s.replaceFirst("<meta property=\"og:image\" content=\"([^\"].*)\"",
				"<meta property=\"og:image\" content=\"" + url + "/med/" + image + "\"/><base href=\"" + client.getUrl()
						+ "/\"");
		while (s.contains("(<link rel=\"alternate\""))
			s = s.replaceFirst("(<link rel=\"alternate\" ([^>].*)>)", "");
		if (!Strings.isEmpty(title)) {
			title = Strings.sanitize(title.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ').replace('"', '\''),
					0).replace("null", "").trim();
			s = s.replaceFirst("</add>",
					"<style>article{opacity:0;position:absolute;}</style><article>" + title
							+ "<figure><img src=\"" + url + "/med/" + image + "\"/></figure></article></add>");
			s = s.replaceFirst("<meta name=\"description\" content=\"([^\"].*)\"",
					"<meta name=\"description\" content=\"" + title + '"');
			s = s.replaceFirst("<title></title>", "<title>" +
					(client.getName() + " · " + (title.length() > 200 ? title.substring(0, 200) : title)) + "</title>");
		}
		return s;
	}
}
