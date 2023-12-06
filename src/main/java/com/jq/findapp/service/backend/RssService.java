package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.util.EntityUtil;

@Service
public class RssService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			try {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString());
				if (json.has("rss")) {
					final boolean publish = json.get("rss").get("publish").asBoolean();
					final String count = syncFeed(json.get("rss").get("url").asText(),
							(BigInteger) list.get(i).get("client.id"), publish);
					if (count != null)
						result.result += list.get(i).get("client.id") + ": " + count + "\n";
				}
			} catch (final Exception e) {
				result.result += "Error " + e.getMessage() + " on client " + list.get(i).get("client.id") + "\n";
				if (result.exception == null)
					result.exception = e;
			}
		}
		return result;
	}

	private String syncFeed(final String url, final BigInteger clientId, final boolean publish) throws Exception {
		final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(new URL(url)).get("channel").get("item");
		if (rss == null || rss.size() == 0)
			return null;
		final QueryParams params = new QueryParams(Query.misc_listNews);
		final Contact contact = new Contact();
		contact.setClientId(clientId);
		params.setUser(contact);
		final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
		final Set<String> urls = new HashSet<>();
		int count = 0;
		Result result;
		for (int i = 0; i < rss.size(); i++) {
			final String uid = rss.get(i).get("guid").asText();
			params.setSearch("clientNews.url='" + uid + "'");
			result = repository.list(params);
			final ClientNews clientNews;
			if (result.size() == 0)
				clientNews = new ClientNews();
			else {
				clientNews = repository.one(ClientNews.class, (BigInteger) result.get(0).get("clientNews.id"));
				clientNews.historize();
			}
			clientNews.setClientId(clientId);
			clientNews.setDescription(rss.get(i).get("title").asText());
			clientNews.setUrl(uid);
			clientNews.setPublish(new Timestamp(df.parse(rss.get(i).get("pubDate").asText()).getTime()));
			final Matcher matcher = img
					.matcher(IOUtils.toString(new URL(rss.get(i).get("link").asText()), StandardCharsets.UTF_8)
							.replace('\r', ' ').replace('\n', ' '));
			if (matcher.find()) {
				String s = matcher.group(1);
				if (!s.startsWith("http"))
					s = url.substring(0, url.indexOf("/", 10)) + s;
				clientNews.setImage(EntityUtil.getImage(s, EntityUtil.IMAGE_SIZE, 200));
			} else
				clientNews.setImage(null);
			if (clientNews.getId() == null)
				count++;
			final boolean b = publish && clientNews.getId() == null;
			repository.save(clientNews);
			urls.add(clientNews.getUrl());
			if (b)
				externalService.publishOnFacebook(clientId, clientNews.getDescription(),
						repository.one(Client.class, clientId).getUrl()
								+ "/rest/action/marketing/news/" + clientNews.getId());
		}
		params.setSearch("clientNews.publish>'"
				+ new Timestamp(df.parse(rss.get(rss.size() - 1).get("pubDate").asText()).getTime())
				+ "' and clientNews.clientId=" + clientId);
		result = repository.list(params);
		int deleted = 0;
		for (int i = 0; i < result.size(); i++) {
			if (!urls.contains(result.get(i).get("clientNews.url"))) {
				repository.delete(repository.one(ClientNews.class, (BigInteger) result.get(i).get("clientNews.id")));
				deleted++;
			}
		}
		if (count == 0 && deleted == 0)
			return null;
		return count + (deleted > 0 ? "/" + deleted : "");
	}
}
