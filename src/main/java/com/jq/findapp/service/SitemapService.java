package com.jq.findapp.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Group;
import com.jq.findapp.service.CronService.Job;
import com.jq.findapp.util.Json;

@Service
public class SitemapService {
	@Autowired
	private Repository repository;

	@Job(cron = "0 20", group = Group.Four)
	public CronResult job() {
		final CronResult result = new CronResult();
		repository.list(new QueryParams(Query.misc_listClient)).forEach(e -> {
			try {
				final JsonNode json = Json.toNode(e.get("client.storage").toString()).get("sitemap");
				if (json != null) {
					final StringBuilder sitemap = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					sitemap.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
					if (json.get("type").asText().contains("event")) {
						final QueryParams params = new QueryParams(Query.event_listId);
						params.setSearch(
								"event.contactId=" + e.get("client.adminId")
										+ " and event.endDate>cast('"
										+ Instant.now() + "' as timestamp)");
						writeMap(json, "event", params, sitemap, (String) e.get("client.url"));
					}
					if (json.get("type").asText().contains("news")) {
						final QueryParams params = new QueryParams(Query.misc_listNews);
						params.setUser(new Contact());
						params.getUser().setClientId((BigInteger) e.get("client.id"));
						params.setSearch("clientNews.publish<cast('" + Instant.now() + "' as timestamp)");
						writeMap(json, "news", params, sitemap, (String) e.get("client.url"));
					}
					if (json.get("type").asText().contains("location"))
						writeMap(json, "location", new QueryParams(Query.location_listId), sitemap,
								(String) e.get("client.url"));
					sitemap.append("</sitemapindex>");
					IOUtils.write(sitemap.toString().getBytes(StandardCharsets.UTF_8),
							new FileOutputStream(json.get("path").asText() + File.separatorChar + "sitemap.xml"));
					result.body += "updated " + e.get("client.id");
				}
			} catch (final Exception ex) {
				result.body += e.get("client.id") + ", error " + ex.getMessage() + "\n";
				if (result.exception == null)
					result.exception = ex;
			}
		});
		return result;
	}

	private void writeMap(final JsonNode json, final String type, final QueryParams params,
			final StringBuilder sitemap, final String url) throws Exception {
		params.setLimit(0);
		final String urlList = url + "/rest/marketing/" + type + "/";
		final String name = "sitemap_" + type + ".txt";
		final String filename = json.get("path").asText() + File.separatorChar + name;
		new File(filename).delete();
		try (final FileOutputStream out = new FileOutputStream(filename)) {
			repository.list(params).forEach(
					e -> {
						try {
							out.write((urlList + e.get(("news".equals(type) ? "clientNews" : type) + ".id") + "\n")
									.getBytes(StandardCharsets.UTF_8));
						} catch (IOException ex) {
							throw new RuntimeException(ex);
						}
					});
		}
		sitemap.append("<sitemap><loc>" + url + "/" + name + "</loc></sitemap>");
	}
}
