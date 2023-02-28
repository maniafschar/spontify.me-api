package com.jq.findapp.service.backend;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
	private String lookupIp;

	private static final ArrayNode webCalls;

	static {
		try {
			webCalls = (ArrayNode) new ObjectMapper()
					.readTree(ImportLogService.class.getResourceAsStream("/webCalls.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

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

	public String[] mapWebCalls() {
		final String[] result = new String[] { getClass().getSimpleName() + "/mapWebCalls", null };
		try {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setSearch("log.webCall is not null");
			params.setLimit(1);
			Result r = repository.list(params);
			final Timestamp timestamp;
			if (r.size() > 0)
				timestamp = (Timestamp) r.get(0).get("log.createdAt");
			else
				timestamp = Timestamp.from(Instant.now().minus(Duration.ofDays(31)));
			params.setSearch(
					"log.webCall is null and length(log.method)>0 and log.uri not like '/support/%' and log.uri like '/%' and log.createdAt>='"
							+ timestamp + "'");
			params.setLimit(0);
			r = repository.list(params);
			for (int i = 0; i < r.size(); i++)
				mapCall(repository.one(Log.class, (BigInteger) r.get(i).get("log.id")));

		} catch (Exception e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}

	String mapCall(Log log) throws Exception {
		Object[] list = StreamSupport.stream(webCalls.spliterator(), false)
				.filter(e -> e.get("url").asText()
						.startsWith(log.getMethod() + " " + log.getUri().substring(1) +
								(e.get("url").asText().contains("?") ? "?" : "")))
				.toArray();
		if (list.length > 1) {
			list = filterByQueryOrClass(list, log);
			if (list.length > 1)
				list = filterOtherParams(list, log);
		}
		if (list.length == 0) {
			if (log.getCreatedAt().toInstant().isAfter(Instant.now().minus(Duration.ofDays(4))))
				System.out.println(
						"no web call for " + log.getMethod() + log.getUri() + log.getQuery() + log.getBody());
		} else if (list.length > 2 || list.length == 2
				&& !((JsonNode) list[0]).get("call").asText()
						.equals(((JsonNode) list[1]).get("call").asText())
				&& (!((JsonNode) list[0]).has("body") ||
						!((JsonNode) list[1]).has("body") ||
						!((JsonNode) list[0]).get("body").asText()
								.equals(((JsonNode) list[1]).get("body").asText()))) {
			if (log.getCreatedAt().toInstant().isAfter(Instant.now().minus(Duration.ofDays(4))))
				System.out.println(
						"too many web calls for " + log.getMethod() + log.getUri() + log.getQuery()
								+ log.getBody()
								+ ": " + list.length);
		} else {
			log.setWebCall(((JsonNode) list[0]).get("call").asText());
			repository.save(log);
			return log.getWebCall();
		}
		return null;
	}

	private Object[] filterByQueryOrClass(final Object[] list, final Log log) {
		if (!Strings.isEmpty(log.getQuery())) {
			final Optional<String> s = Arrays.asList(log.getQuery().split("&")).stream()
					.filter(e2 -> e2.startsWith("query=")).findFirst();
			if (s.isPresent()) {
				return Arrays.asList(list).stream().filter(e -> {
					final String url = ((JsonNode) e).get("url").asText();
					return url.contains(s.get() + (url.contains("&") ? "&" : ""));
				}).toArray();
			}
		} else if (!Strings.isEmpty(log.getBody()) && log.getBody().contains("\"classname\":\"")) {
			String s = log.getBody();
			s = s.substring(s.indexOf("\"classname\":\"") + 13);
			final String name = s.contains("\"") ? s.substring(0, s.indexOf("\"")) : s;
			return Arrays.asList(list).stream().filter(e -> {
				return ((JsonNode) e).has("body") && ((JsonNode) e).get("body").asText().contains(name);
			}).toArray();
		}
		return list;
	}

	private Object[] filterOtherParams(final Object[] list, final Log log) {
		if (!Strings.isEmpty(log.getQuery())) {
			String[] s = log.getQuery().split("&");
			String s2 = "";
			for (int i = 0; i < s.length; i++)
				s2 += "&" + s[i].split("=")[0] + "=.*";
			final String patternQuery = ".*" + s2.substring(1);
			Object[] l = Arrays.asList(list).stream().filter(e -> {
				return ((JsonNode) e).get("url").asText().matches(patternQuery);
			}).toArray();
			if (l.length > 1) {
				if (log.getQuery().contains("search=")) {
					s = log.getQuery().substring(log.getQuery().indexOf("search=") + 7).split("&")[0].split(" ");
					s2 = "";
					for (int i = 0; i < s.length; i++) {
						if (s[i].contains("=")) {
							s[i] = s[i].split("=")[0];
							if (s[i].matches("[a-zA-Z.0-9]*"))
								s2 += "&" + s[i] + "=.*";
						}
					}
					if (s2.length() > 0) {
						final String patternSearch = ".*" + s2.substring(1);
						return Arrays.asList(l).stream().filter(e -> {
							return ((JsonNode) e).get("url").asText().matches(patternSearch);
						}).toArray();
					}
				}
				if (log.getQuery().length() < 20)
					return Arrays.asList(l).stream().filter(e -> {
						return ((JsonNode) e).get("url").asText()
								.equals(log.getMethod() + " " + log.getUri().substring(1) + "?" + log.getQuery());
					}).toArray();
			}
			return l;
		}
		if (!Strings.isEmpty(log.getBody()) && log.getBody().contains("\"values\":{")) {
			final String s = log.getBody().substring(log.getBody().indexOf("\"values\":{") + 10)
					.replace("\"", "")
					.split(",")[0].split(":")[0];
			return Arrays.asList(list).stream().filter(e -> {
				if (!((JsonNode) e).has("body"))
					return false;
				return ((JsonNode) e).get("body").asText().contains(s);
			}).toArray();
		}
		return list;
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
					final String path = m.group(6);
					final Log log = new Log();
					log.setIp(m.group(1));
					log.setMethod(m.group(5));
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
					if ("/stats.html".equals(path) || (!path.contains(".")
							&& !path.contains("apple-app-site-association")
							&& !path.startsWith("/rest/")
							&& log.getStatus() < 400)) {
						log.setUri(name.contains("Ad") ? "ad" : "web");
						if (path.length() > 2) {
							final String[] s = path.split("\\?");
							if (s[0].length() > 1)
								log.setUri(log.getUri() + URLDecoder.decode(s[0], StandardCharsets.UTF_8));
							if (s.length > 1)
								log.setQuery(s[1]);
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
		final Set<Object> processed = new HashSet<>();
		for (int i = 0; i < result.size(); i++) {
			if (!processed.contains(result.get(i).get("log.ip"))) {
				processed.add(result.get(i).get("log.ip"));
				final String json = WebClient
						.create(lookupIp.replace("{ip}", (String) result.get(i).get("log.ip"))).get()
						.retrieve().toEntity(String.class).block().getBody();
				final Ip ip = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
						.readValue(json, Ip.class);
				final String location = new ObjectMapper().readTree(json.getBytes(StandardCharsets.UTF_8)).get("loc")
						.asText();
				ip.setLatitude(Float.parseFloat(location.split(",")[0]));
				ip.setLongitude(Float.parseFloat(location.split(",")[1]));
				repository.save(ip);
			}
		}
	}
}