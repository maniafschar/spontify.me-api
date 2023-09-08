package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
					final Result listContact = repository.list(params);
					if (listContact.size() > 0) {
						final int count = syncFeed(json.get("rss").asText(),
								(BigInteger) listContact.get(0).get("contact.id"));
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

	private int syncFeed(final String url, final BigInteger contactId) throws Exception {
		final ArrayNode rss = (ArrayNode) new XmlMapper().readTree(new URL(url)).get("channel").get("item");
		if (rss == null || rss.size() == 0)
			return 0;
		final QueryParams params = new QueryParams(Query.contact_listNews);
		params.setUser(repository.one(Contact.class, contactId));
		final Pattern img = Pattern.compile("\\<article.*?\\<figure.*?\\<img .*?src=\\\"(.*?)\\\"");
		int count = 0;
		for (int i = 0; i < rss.size(); i++) {
			params.setSearch(
					"contactNews.url='" + rss.get(i).get("guid").asText() + "'");
			if (repository.list(params).size() == 0) {
				final ContactNews contactNews = new ContactNews();
				contactNews.setContactId(BigInteger.ONE);
				contactNews.setDescription(rss.get(i).get("title").asText());
				contactNews.setUrl(rss.get(i).get("guid").asText());
				final String html = IOUtils.toString(new URL(contactNews.getUrl()), StandardCharsets.UTF_8);
				final Matcher matcher = img.matcher(html);
				if (matcher.find())
					contactNews.setImage(matcher.group(1));
				repository.save(contactNews);
				count++;
			}
		}
		return count;
	}
}