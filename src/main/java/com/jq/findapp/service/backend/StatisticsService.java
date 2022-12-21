package com.jq.findapp.service.backend;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class StatisticsService {
	@Autowired
	private Repository repository;

	public String[] update() {
		final String[] result = new String[] { getClass().getSimpleName() + "/update", null };
		try {
			final Map<String, Object> data = new HashMap<>();
			final QueryParams params = new QueryParams(Query.misc_statsUser);
			params.setLimit(0);
			data.put("user", repository.list(params).getList());
			params.setQuery(Query.misc_statsLog);
			data.put("log", repository.list(params).getList());
			params.setQuery(Query.misc_statsApi);
			data.put("api", repository.list(params).getList());
			params.setQuery(Query.misc_statsLocations);
			params.setLimit(200);
			data.put("locations", repository.list(params).getList());
			data.put("update", Instant.now().toString());
			IOUtils.write(new ObjectMapper().writeValueAsString(data), new FileOutputStream("statistics.json"),
					StandardCharsets.UTF_8);
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}
}