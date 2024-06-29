package com.jq.findapp.api;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.IpService;
import com.jq.findapp.service.backend.MarketingService;
import com.jq.findapp.util.Strings;

import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("marketing")
public class MarketingApi {
	private static final Map<BigInteger, String> INDEXES = new HashMap<>();
	private static final Map<BigInteger, String> MENU = new HashMap<>();
	private static volatile long lastUpdate = 0;

	@Autowired
	private Repository repository;

	@Autowired
	private MarketingService marketingService;

	@Autowired
	private NotificationService notificationService;

	@PostMapping
	public BigInteger pollAnswerCreate(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger clientId,
			@RequestHeader(required = false) final BigInteger user) throws Exception {
		final ContactMarketing contactMarketing = new ContactMarketing();
		contactMarketing.populate(entity.getValues());
		if (repository.one(ClientMarketing.class, contactMarketing.getClientMarketingId()).getClientId()
				.equals(clientId)) {
			contactMarketing.setContactId(user);
			repository.save(contactMarketing);
			return contactMarketing.getId();
		}
		return null;
	}

	@PutMapping
	public String pollAnswerSave(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger clientId)
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
			return marketingService.locationUpdate(contactMarketing);
		}
		return null;
	}

	@GetMapping
	public Map<String, Object> poll(@RequestHeader final BigInteger clientId,
			@RequestHeader(required = false) final BigInteger user,
			@RequestParam(name = "m") final BigInteger clientMarketingId,
			@RequestParam(name = "i", required = false) final BigInteger locationId,
			@RequestParam(name = "h", required = false) final Integer hash,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
		if (clientMarketing != null && clientId.equals(clientMarketing.getClientId())) {
			if (clientMarketing.getEndDate() != null
					&& clientMarketing.getEndDate().getTime() < Instant.now().getEpochSecond() * 1000) {
				final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
				params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
				return repository.one(params);
			}
			final QueryParams params = new QueryParams(Query.misc_listMarketing);
			params.setSearch("clientMarketing.id=" + clientMarketingId);
			final Map<String, Object> result = repository.one(params);
			if (locationId != null) {
				params.setQuery(Query.location_listId);
				params.setSearch("location.id=" + locationId);
				final Location location = repository.one(Location.class,
						(BigInteger) repository.one(params).get("location.id"));
				if (location == null || location.getSecret().hashCode() != hash ||
						location.getUpdatedAt() != null
								&& location.getUpdatedAt().after(clientMarketing.getStartDate()))
					return null;
				result.put("_address", location.getAddress());
				result.put("_description", location.getDescription());
				result.put("_name", location.getName());
				result.put("_skills", location.getSkills());
				result.put("_telephone", location.getTelephone());
				result.put("_url", location.getUrl());
			} else if (((String) result.get("clientMarketing.storage")).contains("locationMarketing"))
				return null;
			if (!finished(result, clientId, user, clientMarketingId, ip))
				return result;
		}
		return null;
	}

	private boolean finished(final Map<String, Object> result, final BigInteger clientId,
			final BigInteger user, final BigInteger clientMarketingId, final String ip) {
		if (user == null) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setSearch("log.ip='" + IpService.sanatizeIp(ip)
					+ "' and log.createdAt>cast('" + Instant.now().minus(Duration.ofHours(6)).toString()
					+ "' as timestamp) and log.uri='/marketing' and log.status<300 and log.method='POST' and log.clientId="
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
								+ clientMarketingId);
				final Result list2 = repository.list(params);
				if (list2.size() > 0)
					result.put("_answer", list2.get(0));
			}
		} else {
			final QueryParams params = new QueryParams(Query.contact_listMarketing);
			params.setSearch("contactMarketing.contactId=" + user + " and contactMarketing.clientMarketingId="
					+ clientMarketingId);
			final Result list = repository.list(params);
			if (list.size() > 0)
				result.put("_answer", list.get(0));
		}
		@SuppressWarnings("unchecked")
		final Boolean finished = result.containsKey("_answer") ? (Boolean) ((Map<String, Object>) result.get("_answer"))
				.get("contactMarketing.finished") : null;
		return finished != null && finished;
	}

	@GetMapping(path = { "{id}", "{id}/result" }, produces = MediaType.TEXT_HTML_VALUE)
	public String poll(@PathVariable final BigInteger id) throws Exception {
		final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, id);
		if (clientMarketing == null)
			return "";
		final String image;
		if (clientMarketing.getEndDate() != null
				&& clientMarketing.getEndDate().before(new Timestamp(Instant.now().toEpochMilli()))) {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
			final Result result = repository.list(params);
			if (result.size() == 0 || result.get(0).get("clientMarketingResult.image") == null)
				return "";
			image = result.get(0).get("clientMarketingResult.image").toString();
		} else
			image = Attachment.resolve(clientMarketing.getImage());
		return getHtml(repository.one(Client.class, clientMarketing.getClientId()), "" + id, image, "");
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
		final Location location = event.getLocationId() == null ? null
				: repository.one(Location.class, event.getLocationId());
		String image = event.getImage();
		if (image == null && location != null)
			image = location.getImage();
		if (image == null)
			image = contact.getImage();
		return getHtml(repository.one(Client.class, contact.getClientId()), "event/" + id,
				Attachment.resolve(image),
				event.getDescription()
						+ (location == null ? "" : " " + location.getAddress() + " " + location.getDescription()));
	}

	private synchronized void update() throws IOException {
		if (System.currentTimeMillis() - lastUpdate > 24 * 60 * 60 * 1000) {
			lastUpdate = System.currentTimeMillis();
			final Result list = repository.list(new QueryParams(Query.misc_listClient));
			for (int i = 0; i < list.size(); i++) {
				final Client client = repository.one(Client.class, (BigInteger) list.get(i).get("client.id"));
				try (final InputStream in = URI.create(client.getUrl()).toURL().openStream()) {
					INDEXES.put(client.getId(), IOUtils.toString(in, StandardCharsets.UTF_8));
				}
				final String url = Strings.removeSubdomain(client.getUrl());
				final StringBuilder s = new StringBuilder();
				final QueryParams params = new QueryParams(Query.location_listId);
				params.setLimit(0);
				Result result = repository.list(params);
				for (int i2 = 0; i2 < result.size(); i2++)
					s.append("<li><a href=\"" + url + "/rest/marketing/location/" + result.get(i2).get("location.id")
							+ "\">" + result.get(i2).get("location.name") + "</a></li>");
				params.setQuery(Query.event_listId);
				params.setSearch("event.endDate>now() and contact.clientId=" + client.getId());
				result = repository.list(params);
				for (int i2 = 0; i2 < result.size(); i2++)
					s.append("<li><a href=\"" + url + "/rest/marketing/event/" + result.get(i2).get("event.id")
							+ "\">" + result.get(i2).get("event.description") + "</a></li>");
				params.setQuery(Query.misc_listNews);
				params.setSearch(null);
				params.setUser(new Contact());
				params.getUser().setClientId(client.getId());
				result = repository.list(params);
				for (int i2 = 0; i2 < result.size(); i2++)
					s.append("<li><a href=\"" + url + "/rest/marketing/news/" + result.get(i2).get("clientNews.id")
							+ "\">" + result.get(i2).get("clientNews.description") + "</a></li>");
				MENU.put(client.getId(), s.toString());
			}
		}
	}

	private String getHtml(final Client client, final String path, final String image, String title)
			throws IOException {
		update();
		String s = INDEXES.get(client.getId());
		final String url = Strings.removeSubdomain(client.getUrl());
		s = s.replaceFirst("<meta property=\"og:url\" content=\"([^\"].*)\"",
				"<meta property=\"og:url\" content=\"" + url + "/rest/marketing/" + path + '"');
		s = s.replaceFirst("<link rel=\"canonical\" href=\"([^\"].*)\"",
				"<link rel=\"canonical\" href=\"" + url + "/rest/marketing/" + path + '"');
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
							+ "<figure><img src=\"" + url + "/med/" + image + "\"/></figure><menu><ul>"
							+ MENU.get(client.getId()) + "</ul></menu></article></add>");
			s = s.replaceFirst("<meta name=\"description\" content=\"([^\"].*)\"",
					"<meta name=\"description\" content=\"" + title + '"');
			s = s.replaceFirst("<title></title>", "<title>" +
					(client.getName() + " · " + (title.length() > 200 ? title.substring(0, 200) : title)) + "</title>");
		}
		return s;
	}
}
