package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Locale;
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
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.ContactNews;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;

@Service
public class RssService {
	@Autowired
	private NotificationService notificationService;

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
					final Result listContact = repository.list(params);
					if (listContact.size() > 0) {
						final int count = syncFeed(json.get("rss").asText(),
								repository.one(Contact.class, (BigInteger) listContact.get(0).get("contact.id")));
						if (count > 0)
							result.result += list.get(i).get("client.id") + ": " + count + "\n";
					} else
						result.result += list.get(i).get("client.id") + ": " + ContactType.adminContent.name()
								+ " not found\n";
				}
			}
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private int syncFeed(final String url, final Contact contact) throws Exception {
		final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(new URL(url)).get("channel").get("item");
		if (rss == null || rss.size() == 0)
			return 0;
		final QueryParams params = new QueryParams(Query.contact_listNews);
		params.setUser(contact);
		final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		final SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ROOT);
		int count = 0;
		for (int i = 0; i < rss.size(); i++) {
			final String uid = rss.get(i).get("guid").get("").asText();
			params.setSearch("contactNews.url='" + uid + "'");
			if (repository.list(params).size() == 0) {
				final ContactNews contactNews = new ContactNews();
				contactNews.setContactId(contact.getId());
				contactNews.setDescription(rss.get(i).get("title").asText());
				contactNews.setUrl(uid);
				contactNews.setPublish(new Timestamp(df.parse(rss.get(i).get("pubDate").asText()).getTime()));
				final Matcher matcher = img
						.matcher(IOUtils.toString(new URL(rss.get(i).get("link").asText()), StandardCharsets.UTF_8)
								.replace('\r', ' ').replace('\n', ' '));
				if (matcher.find())
					contactNews.setImgUrl(matcher.group(1));
				repository.save(contactNews);
				count++;
			}
		}
		return count;
	}
}