package com.jq.findapp.service.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class ImportLogService {
	@Autowired
	private Repository repository;

	@Autowired
	private IpService ipService;

	public SchedulerResult importLog() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importLog");
		try {
			final String[] files = new File(".").list();
			for (String file : files) {
				if (file.startsWith("log") && !file.contains(".") && !new File(file).isDirectory())
					importLog(file);
			}
			ipService.lookupLogIps();
		} catch (Exception e) {
			result.exception = e;
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
			final String uri = name.substring(3).replaceAll("\\d", "").toLowerCase();
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
					if (log.getStatus() < 400 &&
							((path.endsWith(".html") && !path.startsWith("/js/")) || path.endsWith(".pdf") ||
									(!path.contains(".")
											&& !path.contains("apple-app-site-association")
											&& !path.startsWith("/rest/")))) {
						log.setUri(uri);
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
}