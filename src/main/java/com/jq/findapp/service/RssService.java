package com.jq.findapp.service;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.util.Entity;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class RssService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Cron
	public CronResult cron() {
		final CronResult result = new CronResult();
		final Result list = this.repository.list(new QueryParams(Query.misc_listClient));
		final List<CompletableFuture<ImportFeed>> futures = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			final BigInteger clientId = (BigInteger) list.get(i).get("client.id");
			try {
				final JsonNode json = Json.toNode(list.get(i).get("client.storage").toString());
				if (json.has("rss")) {
					for (final JsonNode rss : json.get("rss"))
						futures.add(CompletableFuture.supplyAsync(() -> new ImportFeed(rss, clientId,
								json.has("publishingPostfix") ? json.get("publishingPostfix").asText() : null)));
				}
			} catch (final Exception e) {
				if (result.exception == null)
					result.exception = e;
			}
		}
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.thenApply(ignored -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
				.join();
		final List<String> failed = new ArrayList<>();
		result.body = futures.stream().map(e -> {
			try {
				failed.addAll(e.get().failed);
				return e.get().success;
			} catch (final Exception ex) {
				return ex.getMessage();
			}
		}).collect(Collectors.joining("\n"));
		while (result.body.contains("\n\n"))
			result.body = result.body.replace("\n\n", "\n");
		if (failed.size() > 0)
			this.notificationService.createTicket(TicketType.ERROR, "ImportRss",
					failed.size() + " errors:\n" + failed.stream().sorted().collect(Collectors.joining("\n")), null);
		return result;
	}

	private class ImportFeed {
		private final Pattern[] imageRegex = new Pattern[] {
				Pattern.compile("\\<meta.*?property=\"og:image\".*?content=\\\"(.*?)\\\""),
				Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\""),
				Pattern.compile("\\<div.*?\\<picture.*?\\<img .*?src=\\\"(.*?)\\\"")
		};
		private final SimpleDateFormat dateParser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
		private final SimpleDateFormat dateParserPub = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
		private final Set<String> urls = new HashSet<>();
		private final Set<String> failed = new HashSet<>();
		private String success = "";

		private ImportFeed(final JsonNode json, final BigInteger clientId, final String publishingPostfix) {
			try {
				final String url = json.get("url").asText();
				final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(URI.create(url).toURL()).findValues("item")
						.get(0);
				if (rss != null && rss.size() > 0) {
					final QueryParams params = new QueryParams(Query.misc_listNews);
					params.setUser(new Contact());
					params.getUser().setClientId(clientId);
					int count = 0;
					boolean chonological = true;
					long lastPubDate = 0;
					Timestamp first = new Timestamp(System.currentTimeMillis());
					final boolean addDescription = json.has("description") && json.get("description").asBoolean();
					final String source = json.has("source") ? json.get("source").asText() : null;
					final String skills = json.has("skills") ? json.get("skills").asText() : null;
					for (int i = 0; i < rss.size(); i++) {
						try {
							final ClientNews clientNews = this.createNews(params, rss.get(i), addDescription, clientId,
									url, source, skills);
							if (clientNews != null && clientNews.getImage() != null) {
								if (clientNews.getPublish().getTime() > lastPubDate)
									chonological = false;
								else
									lastPubDate = clientNews.getPublish().getTime();
								if (clientNews.getId() == null)
									count++;
								clientNews.setLatitude((float) json.get("latitude").asDouble());
								clientNews.setLongitude((float) json.get("longitude").asDouble());
								final boolean b = json.get("publish").asBoolean() && clientNews.getId() == null;
								RssService.this.repository.save(clientNews);
								if (first.getTime() > clientNews.getPublish().getTime())
									first = clientNews.getPublish();
								this.urls.add(clientNews.getUrl());
								if (b) {
									final String fbId = RssService.this.externalService.publishOnFacebook(clientId,
											clientNews.getDescription()
													+ (Strings.isEmpty(source) ? "" : "\n" + source)
													+ (Strings.isEmpty(publishingPostfix) ? ""
															: "\n\n" + publishingPostfix),
											"/rest/marketing/news/" + clientNews.getId());
									if (fbId != null) {
										clientNews.setPublishId(fbId);
										RssService.this.repository.save(clientNews);
									}
								}
							}
						} catch (final Exception ex) {
							this.addFailure(ex, url);
						}
					}
					if (count != 0)
						this.success += count + " on " + clientId + " " + url;
				}
			} catch (final Exception ex) {
				this.addFailure(ex, this.success + json.get("url").asText());
			}
		}

		private void addFailure(final Exception ex, final String url) {
			this.failed.add((ex.getMessage().startsWith("IMAGE_") ? "" : ex.getClass().getName() + ": ")
					+ ex.getMessage().replace("\n", "\n  ") + "\n  " + url);
		}

		private ClientNews createNews(final QueryParams params, final JsonNode rss, final boolean addDescription,
				final BigInteger clientId, final String url, final String source, final String skills)
				throws Exception {
			String uid = null;
			if (rss.has("link"))
				uid = rss.get("link").asText().trim();
			if (uid == null) {
				uid = rss.get("guid").asText().trim();
				if (Strings.isEmpty(uid))
					uid = rss.get("guid").get("").asText().trim();
			}
			final int max = 1000;
			final String description = Strings.sanitize(rss.get("title").asText() +
					(addDescription && rss.has("description")
							? "\n" + rss.get("description").asText()
							: ""),
					max).trim();
			params.setSearch("clientNews.url='" + uid + "' and clientNews.clientId=" + clientId);
			Result result = RssService.this.repository.list(params);
			final ClientNews clientNews;
			if (result.size() == 0) {
				params.setSearch("clientNews.description like '" + description.replace('\'', '_').replace('\n', '_')
						+ "' and clientNews.clientId=" + clientId);
				result = RssService.this.repository.list(params);
			}
			if (result.size() > 0)
				return null;
			clientNews = new ClientNews();
			clientNews.setSource(source);
			clientNews.setClientId(clientId);
			clientNews.setDescription(description);
			clientNews.setSkills(skills);
			clientNews.setUrl(uid);
			if (rss.has("pubDate"))
				clientNews.setPublish(new Timestamp(this.dateParserPub.parse(rss.get("pubDate").asText()).getTime()));
			else
				clientNews.setPublish(new Timestamp(this.dateParser.parse(rss.get("date").asText()).getTime()));
			if (rss.has("media:content") && rss.get("media:content").has("url"))
				clientNews.setImage(Entity.getImage(rss.get("media:content").get("url").asText(),
						Entity.IMAGE_SIZE, 200));
			else {
				final String html = IOUtils.toString(new URI(uid), StandardCharsets.UTF_8)
						.replace('\r', ' ').replace('\n', ' ');
				String s = null;
				for (final Pattern pattern : this.imageRegex) {
					final Matcher matcher = pattern.matcher(html);
					if (matcher.find()) {
						s = matcher.group(1);
						break;
					}
				}
				if (s != null) {
					if (s.startsWith("/"))
						s = url.substring(0, url.indexOf("/", 10)) + s;
					clientNews.setImage(Entity.getImage(s, Entity.IMAGE_SIZE, 200));
				}
			}
			return clientNews;
		}
	}
}
