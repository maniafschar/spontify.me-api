package com.jq.findapp.service.backend;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class SitemapService {
	@Autowired
	private Repository repository;

	private static final AtomicLong lastRun = new AtomicLong(0);

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		if (lastRun.get() < System.currentTimeMillis() - 2 * 60 * 60 * 1000) {
			lastRun.set(System.currentTimeMillis());
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
									"event.contactId=" + list.get(i).get("client.adminId") + " and event.endDate>'"
											+ Instant.now() + "'");
							writeMap(json, "event", params, sitemap);
						}
						if (json.get("type").asText().contains("news")) {
							final QueryParams params = new QueryParams(Query.misc_listNews);
							params.setSearch(
									"clientNews.clientId=" + list.get(i).get("client.id") + " and event.publish>'"
											+ Instant.now() + "'");
							writeMap(json, "news", params, sitemap);
						}
						if (json.get("type").asText().contains("location"))
							writeMap(json, "location", new QueryParams(Query.location_listId), sitemap);
						sitemap.append("</sitemapindex>");
						IOUtils.write(sitemap.toString().getBytes(StandardCharsets.UTF_8),
								new FileOutputStream(""));
						result.result += "updated " + list.get(i).get("client.id");
					}
				} catch (final Exception e) {
					result.result += list.get(i).get("client.id") + ", error " + e.getMessage() + "\n";
					if (result.exception == null)
						result.exception = e;
				}
			}
		} else
			result.result = "paused";
		return result;
	}

	private void writeMap(final JsonNode json, final String type, final QueryParams params,
			final StringBuilder sitemap) throws Exception {
		final StringBuilder map = new StringBuilder();
		final Result list = repository.list(params);
		final String url = Strings.removeSubdomain(json.get("client.url").asText());
		final String urlList = url + "/rest/action/marketing/" + type + "/";
		final String name = "sitemap_" + type + ".xml";
		for (int i2 = 0; i2 < list.size(); i2++)
			map.append(urlList + list.get(i2).get(type + ".id") + "\n");
		IOUtils.write(map.toString().getBytes(StandardCharsets.UTF_8),
				new FileOutputStream(json.get("path").asText() + File.pathSeparatorChar + name));
		sitemap.append("<sitemap><loc>" + url + "/" + name + "</loc></sitemap>");
	}
}
