package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URL;
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
		final CompletableFuture<Object> all = CompletableFuture
				.allOf(futures.toArray(new CompletableFuture[futures.size()]))
				.thenApply(ignored -> futures.stream()
						.map(CompletableFuture::join).collect(Collectors.toList()));
		all.join();
		result.result = futures.stream().map(e -> {
			try {
				return e.get().toString();
			} catch (final Exception ex) {
				return ex.getMessage();
			}
		}).collect(Collectors.joining("\n"));
		return result;
	}

	@Async
	private CompletableFuture<Object> syncFeed(final JsonNode json, final BigInteger clientId) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				final String url = json.get("url").asText();
				final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(new URL(url)).get("channel")
						.get("item");
				if (rss == null || rss.size() == 0)
					return null;
				final QueryParams params = new QueryParams(Query.misc_listNews);
				params.setUser(new Contact());
				params.getUser().setClientId(clientId);
				final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
				final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
				final Set<String> urls = new HashSet<>();
				int count = 0;
				boolean chonological = true;
				long lastPubDate = 0;
				Timestamp first = new Timestamp(System.currentTimeMillis());
				Result result;
				for (int i = 0; i < rss.size(); i++) {
					String uid = null;
					if (rss.get(i).has("guid")) {
						uid = rss.get(i).get("guid").asText().trim();
						if (Strings.isEmpty(uid))
							uid = rss.get(i).get("guid").get("").asText().trim();
					}
					if (uid == null || !uid.startsWith("https://"))
						uid = rss.get(i).get("link").asText().trim();
					if (!Strings.isEmpty(uid)) {
						params.setSearch("clientNews.url='" + uid + "' and clientNews.clientId=" + clientId);
						result = repository.list(params);
						final ClientNews clientNews;
						if (result.size() == 0)
							clientNews = new ClientNews();
						else {
							clientNews = repository.one(ClientNews.class,
									(BigInteger) result.get(0).get("clientNews.id"));
							clientNews.historize();
						}
						clientNews.setClientId(clientId);
						clientNews.setLatitude((float) json.get("latitude").asDouble());
						clientNews.setLongitude((float) json.get("longitude").asDouble());
						clientNews.setDescription(Strings.sanitize(rss.get(i).get("title").asText() +
								(json.has("description") && json.get("description").asBoolean()
										&& rss.get(i).has("description")
												? "\n\n" + rss.get(i).get("description").asText()
												: "")));
						clientNews.setUrl(uid);
						clientNews.setPublish(new Timestamp(df.parse(rss.get(i).get("pubDate").asText()).getTime()));
						if (clientNews.getPublish().getTime() > lastPubDate)
							chonological = false;
						else
							lastPubDate = clientNews.getPublish().getTime();
						clientNews.setImage(null);
						try {
							if (rss.get(i).has("media:content") && rss.get(i).get("media:content").has("url"))
								clientNews.setImage(
										EntityUtil.getImage(rss.get(i).get("media:content").get("url").asText(),
												EntityUtil.IMAGE_SIZE, 200));
							else {
								final Matcher matcher = img
										.matcher(IOUtils
												.toString(new URL(rss.get(i).get("link").asText()),
														StandardCharsets.UTF_8)
												.replace('\r', ' ').replace('\n', ' '));
								if (matcher.find()) {
									String s = matcher.group(1);
									if (!s.startsWith("http"))
										s = url.substring(0, url.indexOf("/", 10)) + s;
									clientNews.setImage(EntityUtil.getImage(s, EntityUtil.IMAGE_SIZE, 200));
								}
							}
							if (clientNews.getImage() != null) {
								if (clientNews.getId() == null)
									count++;
								final boolean b = json.get("publish").asBoolean() && clientNews.getId() == null;
								repository.save(clientNews);
								if (first.getTime() > clientNews.getPublish().getTime())
									first = clientNews.getPublish();
								urls.add(clientNews.getUrl());
								if (b)
									externalService.publishOnFacebook(clientId, clientNews.getDescription(),
											"/rest/action/marketing/news/" + clientNews.getId());
							}
						} catch (final IllegalArgumentException ex) {
							notificationService.createTicket(TicketType.ERROR, "RSS Import Image",
									Strings.stackTraceToString(ex), clientId);
						}
					}
				}
				params.setSearch("clientNews.publish>'" + first + "' and clientNews.clientId=" + clientId);
				result = repository.list(params);
				int deleted = 0;
				if (chonological) {
					for (int i = 0; i < result.size(); i++) {
						if (!urls.contains(result.get(i).get("clientNews.url"))) {
							// TODO rm repository.delete(repository.one(ClientNews.class, (BigInteger)
							// result.get(i).get("clientNews.id")));
							notificationService.createTicket(TicketType.ERROR, "RSS deletion",
									result.get(i).get("clientNews.id") + "\n"
											+ result.get(i).get("clientNews.description")
											+ "\n" + result.get(i).get("clientNews.url"),
									clientId);
							deleted++;
						}
					}
				}
				if (count != 0 || deleted != 0)
					return clientId + " " + count + (deleted > 0 ? "/" + deleted : "");
			} catch (final Exception ex) {
				notificationService.createTicket(TicketType.ERROR, "RSS Import",
						json.get("url").asText() + "\n" + Strings.stackTraceToString(ex), clientId);
			}
			return "";
		});
	}
}
