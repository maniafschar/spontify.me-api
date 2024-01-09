package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;

@Service
public class RssService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	private final Set<String> failed = Collections.synchronizedSet(new HashSet<>());

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		final List<CompletableFuture<?>> futures = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			try {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString());
				if (json.has("rss")) {
					for (int i2 = 0; i2 < json.get("rss").size(); i2++)
						futures.add(syncFeed(json.get("rss").get(i2), (BigInteger) list.get(i).get("client.id")));
				}
			} catch (final Exception e) {
				if (result.exception == null)
					result.exception = e;
			}
		}
		CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.thenApply(ignored -> futures.stream()
						.map(CompletableFuture::join).collect(Collectors.toList()))
				.join();
		result.result = futures.stream().map(e -> {
			try {
				return e.get().toString();
			} catch (final Exception ex) {
				return ex.getMessage();
			}
		}).collect(Collectors.joining("\n"));
		if (failed.size() > 0)
			notificationService.createTicket(TicketType.ERROR, "ImportRss",
					failed.size() + " errors:\n" + failed.stream().sorted().collect(Collectors.joining("\n")), null);
		return result;
	}

	@Async
	private CompletableFuture<Object> syncFeed(final JsonNode json, final BigInteger clientId) {
		return CompletableFuture.supplyAsync(() -> {
			return new ImportFeed().run(json, clientId);
		});
	}

	private class ImportFeed {
		private final Pattern imageRegex = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		private final Pattern imageRegex2 = Pattern.compile("\\<div.*?\\<picture.*?\\<img .*?src=\\\"(.*?)\\\"");
		private final SimpleDateFormat dateParser = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
		private final SimpleDateFormat dateParser2 = new SimpleDateFormat("yyyy-MM-yy'T'HH:mm:ss.SSSXXX", Locale.ROOT);
		private final Set<String> urls = new HashSet<>();

		private String run(final JsonNode json, final BigInteger clientId) {
			try {
				final String url = json.get("url").asText();
				final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(new URL(url)).findValues("item").get(0);
				if (rss == null || rss.size() == 0)
					return "";
				final QueryParams params = new QueryParams(Query.misc_listNews);
				params.setUser(new Contact());
				params.getUser().setClientId(clientId);
				int count = 0;
				boolean chonological = true;
				long lastPubDate = 0;
				Timestamp first = new Timestamp(System.currentTimeMillis());
				final boolean addDescription = json.has("description") && json.get("description").asBoolean();
				final String descriptionPostfix = json.has("descriptionPostfix")
						? "\n\n" + json.get("descriptionPostfix").asText()
						: null;
				for (int i = 0; i < rss.size(); i++) {
					try {
						final ClientNews clientNews = createNews(params, rss.get(i), addDescription, clientId, url,
								descriptionPostfix);
						if (clientNews != null) {
							if (clientNews.getPublish().getTime() > lastPubDate)
								chonological = false;
							else
								lastPubDate = clientNews.getPublish().getTime();
							if (clientNews.getImage() != null) {
								if (clientNews.getId() == null)
									count++;
								clientNews.setLatitude((float) json.get("latitude").asDouble());
								clientNews.setLongitude((float) json.get("longitude").asDouble());
								final boolean b = json.get("publish").asBoolean() && clientNews.getId() == null;
								repository.save(clientNews);
								if (first.getTime() > clientNews.getPublish().getTime())
									first = clientNews.getPublish();
								urls.add(clientNews.getUrl());
								if (b)
									externalService.publishOnFacebook(clientId, clientNews.getDescription(),
											"/rest/marketing/news/" + clientNews.getId());
							}
						}
					} catch (final Exception ex) {
						addFailure(ex, url);
					}
				}
				int deleted = 0;
				if (chonological) {
					params.setSearch(
							"clientNews.publish>cast('" + first + "' as timestamp) and clientNews.clientId="
									+ clientId);
					final Result result = repository.list(params);
					for (int i = 0; i < result.size(); i++) {
						if (!urls.contains(result.get(i).get("clientNews.url"))) {
							repository.delete(repository.one(ClientNews.class,
									(BigInteger) result.get(i).get("clientNews.id")));
							notificationService.createTicket(TicketType.ERROR, "RSS Deletion",
									result.get(i).get("clientNews.id") + "\n"
											+ result.get(i).get("clientNews.description")
											+ "\n" + result.get(i).get("clientNews.url"),
									clientId);
							deleted++;
						}
					}
				}
				if (count != 0 || deleted != 0)
					return count + " on " + clientId + " " + url + (deleted > 0 ? ", " + deleted + " deleted" : "");
			} catch (final Exception ex) {
				addFailure(ex, json.get("url").asText());
			}
			return "";
		}

		private synchronized void addFailure(final Exception ex, final String url) {
			failed.add((ex.getMessage().startsWith("IMAGE_") ? "" : ex.getClass().getName() + ": ")
					+ ex.getMessage().replace("\n", "\n  ") + "\n  " + url);
		}

		private ClientNews createNews(final QueryParams params, final JsonNode rss, final boolean addDescription,
				final BigInteger clientId, final String url, final String descriptionPostfix) throws Exception {
			String uid = null;
			if (rss.has("link"))
				uid = rss.get("link").asText().trim();
			if (uid == null) {
				uid = rss.get("guid").asText().trim();
				if (Strings.isEmpty(uid))
					uid = rss.get("guid").get("").asText().trim();
			}
			final int max = 1000;
			String description = Strings.sanitize(rss.get("title").asText() +
					(addDescription && rss.has("description")
							? "\n\n" + rss.get("description").asText()
							: ""),
					max);
			if (!Strings.isEmpty(descriptionPostfix)) {
				if (description.length() + descriptionPostfix.length() > max)
					description = description.substring(0, max - descriptionPostfix.length() - 3) + "...";
				description += descriptionPostfix;
			}
			params.setSearch("clientNews.url='" + uid + "' and clientNews.clientId=" + clientId);
			Result result = repository.list(params);
			final ClientNews clientNews;
			if (result.size() == 0) {
				params.setSearch("clientNews.description like '" + description.replace('\'', '_').replace('\n', '_')
						+ "' and clientNews.clientId=" + clientId);
				result = repository.list(params);
			}
			if (result.size() == 0)
				clientNews = new ClientNews();
			else {
				clientNews = repository.one(ClientNews.class,
						(BigInteger) result.get(0).get("clientNews.id"));
				clientNews.historize();
			}
			clientNews.setClientId(clientId);
			clientNews.setDescription(description);
			clientNews.setUrl(uid);
			if (rss.has("pubDate"))
				clientNews.setPublish(new Timestamp(dateParser.parse(rss.get("pubDate").asText()).getTime()));
			else
				clientNews.setPublish(new Timestamp(dateParser2.parse(rss.get("date").asText()).getTime()));
			clientNews.setImage(null);
			if (rss.has("media:content") && rss.get("media:content").has("url"))
				clientNews.setImage(EntityUtil.getImage(rss.get("media:content").get("url").asText(),
						EntityUtil.IMAGE_SIZE, 200));
			else {
				final String html = IOUtils.toString(new URL(uid), StandardCharsets.UTF_8)
						.replace('\r', ' ').replace('\n', ' ');
				Matcher matcher = imageRegex.matcher(html);
				String s = null;
				boolean found = matcher.find();
				if (!found) {
					matcher = imageRegex2.matcher(html);
					found = matcher.find();
				}
				if (found) {
					s = matcher.group(1);
					if (s.startsWith("/"))
						s = url.substring(0, url.indexOf("/", 10)) + s;
					clientNews.setImage(EntityUtil.getImage(s, EntityUtil.IMAGE_SIZE, 200));
				}
			}
			return clientNews;
		}
	}
}
