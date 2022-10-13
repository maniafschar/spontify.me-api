package com.jq.findapp.service.backend;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class StatisticsService {
	@Autowired
	private Repository repository;

	public void update() throws Exception {
		final Map<String, List<Object[]>> data = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_statsUser);
		params.setLimit(0);
		data.put("user", repository.list(params).getList());
		params.setQuery(Query.misc_statsLog);
		data.put("log", repository.list(params).getList());
		IOUtils.write(new ObjectMapper().writeValueAsString(data), new FileOutputStream("statistics.json"),
				StandardCharsets.UTF_8);
	}
}