package com.jq.findapp.service.backend;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			try {
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString()).get("sitemap");
				if (json != null) {
					if (json.has("events")) {
						final QueryParams params = new QueryParams(Query.event_listId);
						params.setSearch("event.contactId=" + list.get(i).get("client.adminId") + " and event.endDate>'" + Instant.now() + "'");
						writeMap(json, "event", params);
					}
					if (json.has("news")) {
						final QueryParams params = new QueryParams(Query.misc_listNews);
						params.setSearch("clientNews.clientId=" + list.get(i).get("client.id") + " and event.publish>'" + Instant.now() + "'");
						writeMap(json, "news", params);
					}
					if (json.has("locations"))
						writeMap(json, "location", new QueryParams(Query.location_listId));
				}
			} catch (final Exception e) {
				result.result += list.get(i).get("client.id") + ", error " + e.getMessage() + "\n";
				if (result.exception == null)
					result.exception = e;
			}
		}
		return result;
	}
	private void writeMap(final JsonNode json, final String type, final QueryParams params) {
		final StringBuilder map = new StringBuilder();
		final Result list = repository.list(params);
		for (int i2 = 0; i2 < list.size(); i2++)
			map.append(Strings.removeSubdomain(list.get(i).get("client.url")) + "/rest/action/marketing/" + type + "/" + list.get(i2).get(type + ".id") + "\n");
		IOUtils.write(map.toString().getBytes(StandardCharsets.UTF_8), new FileOutputStream(json.get(type).asText()));
	}
}
