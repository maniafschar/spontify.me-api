package com.jq.findapp.service.backend;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ImportLogService {
	@Autowired
	private Repository repository;

	@Value("${app.url.lookupip}")
	private String lookupip;

	public String[] importLog() {
		final String[] result = new String[] { getClass().getSimpleName() + "/importLog", null };
		try {
			importLog("logAd1");
			importLog("logAd");
			importLog("logWeb1");
			importLog("logWeb");
			lookupIps();
		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	private void importLog(final String name) throws Exception {
		final DateFormat dateParser = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);
		final Pattern pattern = Pattern.compile(
				"([\\d.]+) (\\S) (\\S) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\w+) ([^ ]*) ([^\"]*)\" (\\d+) (\\d+) \"([^\"]*)\" \"([^\"]*)\"");
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(name)))) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = pattern.matcher(line.replaceAll("\\\\\"", ""));
				if (m.find()) {
					final Log log = new Log();
					log.setIp(m.group(1));
					log.setMethod(m.group(5));
					log.setQuery(m.group(6));
					log.setStatus(Integer.parseInt(m.group(8)));
					if (!"-".equals(m.group(10))) {
						log.setReferer(m.group(10));
						if (log.getReferer().length() > 255)
							log.setReferer(log.getReferer().substring(0, 255));
					}
					log.setCreatedAt(new Timestamp(dateParser.parse(m.group(4)).getTime()));
					log.setBody(m.group(11).replace("&#39;", "'"));
					if (log.getBody().length() > 255)
						log.setBody(log.getBody().substring(0, 255));
					log.setPort(80);
					if ("/stats.html".equals(log.getQuery()) || (!log.getQuery().contains(".")
							&& !log.getQuery().contains("apple-app-site-association")
							&& !log.getQuery().startsWith("/rest/")
							&& log.getStatus() < 400)) {
						final String uri = name.contains("Ad") ? "ad" : "web";
						if (log.getQuery().startsWith("/?")) {
							log.setUri(uri);
							log.setQuery(log.getQuery().substring(2));
						} else {
							log.setUri(uri + (log.getQuery().length() > 1 ? log.getQuery() : ""));
							log.setQuery("");
						}
						params.setSearch("log.ip='" + log.getIp() + "' and log.uri='" + log.getUri()
								+ "' and log.createdAt='" + Instant.ofEpochMilli(log.getCreatedAt().getTime())
								+ "' and log.body like '" + log.getBody().replace("'", "''") + "'");
						if (repository.list(params).size() == 0)
							repository.save(log);
					}
				}
			}
		}
	}

	private void lookupIps() throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setLimit(0);
		params.setSearch("(log.uri like 'ad%' or log.uri like 'web%') and ip.org is null");
		final Result result = repository.list(params);
		final Pattern patternLoc = Pattern.compile("\"loc\": \"([^\"]*)\"");
		final Set<Object> processed = new HashSet<>();
		for (int i = 0; i < result.size(); i++) {
			if (!processed.contains(result.get(i).get("log.ip"))) {
				processed.add(result.get(i).get("log.ip"));
				final String json = WebClient
						.create(lookupip.replace("{ip}", (String) result.get(i).get("log.ip"))).get()
						.retrieve().toEntity(String.class).block().getBody();
				final Ip ip = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readValue(json, Ip.class);
				final Matcher m = patternLoc.matcher(json);
				m.find();
				final String location = m.group(1);
				ip.setLatitude(Float.parseFloat(location.split(",")[0]));
				ip.setLongitude(Float.parseFloat(location.split(",")[1]));
				repository.save(ip);
			}
		}
	}
}