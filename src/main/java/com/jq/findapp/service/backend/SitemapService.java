package com.jq.findapp.service.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class SitemapService {
	@Autowired
	private Repository repository;

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			try {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString())
						.get("sitemap");
				if (json != null) {
					final StringBuilder sitemap = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					sitemap.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
					if (json.get("type").asText().contains("event")) {
						final QueryParams params = new QueryParams(Query.event_listId);
						params.setSearch(
								"event.contactId=" + list.get(i).get("client.adminId")
										+ " and event.endDate>cast('"
										+ Instant.now() + "' as timestamp)");
						writeMap(json, "event", params, sitemap, (String) list.get(i).get("client.url"));
					}
					if (json.get("type").asText().contains("news")) {
						final QueryParams params = new QueryParams(Query.misc_listNews);
						params.setUser(new Contact());
						params.getUser().setClientId((BigInteger) list.get(i).get("client.id"));
						params.setSearch("clientNews.publish<cast('" + Instant.now() + "' as timestamp)");
						writeMap(json, "news", params, sitemap, (String) list.get(i).get("client.url"));
					}
					if (json.get("type").asText().contains("location"))
						writeMap(json, "location", new QueryParams(Query.location_listId), sitemap,
								(String) list.get(i).get("client.url"));
					sitemap.append("</sitemapindex>");
					IOUtils.write(sitemap.toString().getBytes(StandardCharsets.UTF_8),
							new FileOutputStream(json.get("path").asText() + File.separatorChar + "sitemap.xml"));
					result.result += "updated " + list.get(i).get("client.id");
				}
			} catch (final Exception e) {
				result.result += list.get(i).get("client.id") + ", error " + e.getMessage() + "\n";
				if (result.exception == null)
					result.exception = e;
			}
		}
		return result;
	}

	private void writeMap(final JsonNode json, final String type, final QueryParams params,
			final StringBuilder sitemap, final String url) throws Exception {
		params.setLimit(0);
		final Result list = repository.list(params);
		final String urlList = url + "/rest/marketing/" + type + "/";
		final String name = "sitemap_" + type + ".txt";
		final String filename = json.get("path").asText() + File.separatorChar + name;
		new File(filename).delete();
		try (final FileOutputStream out = new FileOutputStream(filename)) {
			for (int i = 0; i < list.size(); i++)
				out.write((urlList + list.get(i).get(("news".equals(type) ? "clientNews" : type) + ".id") + "\n")
						.getBytes(StandardCharsets.UTF_8));
		}
		sitemap.append("<sitemap><loc>" + url + "/" + name + "</loc></sitemap>");
	}
}
