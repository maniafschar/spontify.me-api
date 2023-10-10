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
import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.EntityUtil;

@Service
public class RssService {
	@Autowired
	private Repository repository;

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			final Result list = repository.list(new QueryParams(Query.misc_listClient));
			for (int i = 0; i < list.size(); i++) {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString());
				if (json.has("rss")) {
					params.setSearch("contact.type='" + ContactType.adminContent.name()
							+ "' and contact.clientId=" + list.get(i).get("client.id"));
					final String count = syncFeed(json.get("rss").asText(),
							(BigInteger) list.get(i).get("client.id"));
					if (count != null)
						result.result += list.get(i).get("client.id") + ": " + count + "\n";
				}
			}
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private String syncFeed(final String url, final BigInteger clientId) throws Exception {
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
			final String uid = rss.get(i).get("guid").get("").asText();
			params.setSearch("clientNews.url='" + uid + "'");
			result = repository.list(params);
			final ClientNews clientNews = result.size() == 0 ? new ClientNews()
					: repository.one(ClientNews.class, (BigInteger) result.get(0).get("clientNews.id"));
			clientNews.setClientId(clientId);
			clientNews.setDescription(rss.get(i).get("title").asText());
			clientNews.setUrl(uid);
			clientNews.setPublish(new Timestamp(df.parse(rss.get(i).get("pubDate").asText()).getTime()));
			final Matcher matcher = img
					.matcher(IOUtils.toString(new URL(rss.get(i).get("link").asText()), StandardCharsets.UTF_8)
							.replace('\r', ' ').replace('\n', ' '));
			if (matcher.find())
				clientNews.setImage(EntityUtil.getImage(matcher.group(1), EntityUtil.IMAGE_SIZE));
			else
				clientNews.setImage(null);
			if (clientNews.getId() == null)
				count++;
			repository.save(clientNews);
			urls.add(clientNews.getUrl());
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