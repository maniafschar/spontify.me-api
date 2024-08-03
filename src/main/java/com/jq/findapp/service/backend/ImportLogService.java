package com.jq.findapp.service.backend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Log;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ImportLogService {
	@Autowired
	private Repository repository;

	@Autowired
	private IpService ipService;

	@Value("${app.importLog.ips2skip}")
	private String ips2skip;

	private static final Map<String, Integer> linesRead = new HashMap<>();

	public SchedulerResult run() {
		final SchedulerResult result = new SchedulerResult();
		try {
			if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 1
					&& Calendar.getInstance().get(Calendar.MINUTE) < 1)
				linesRead.clear();
			final List<String> files = Arrays.asList(new File("./log/").list());
			files.sort((a, b) -> a.compareTo(b));
			for (final String file : files) {
				if (!file.contains(".") && !new File(file).isDirectory()) {
					final int x = importLog(file);
					if (x > 0)
						result.result += x + " " + file.substring(3) + "\n";
				}
			}
			final long t = System.currentTimeMillis();
			final int x = ipService.lookupLogIps();
			result.result += x + " ips updated";
			if (x > 0)
				result.result += " in " + (System.currentTimeMillis() - t) + " ms";
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private int importLog(final String filename) throws Exception {
		int count = 0;
		final DateFormat dateParser = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);
		final Pattern pattern = Pattern.compile(
				"([\\d.]+) (\\S) (\\S) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\w+) ([^ ]*) ([^\"]*)\" (\\d+) (\\d+) \"([^\"]*)\" \"([^\"]*)\"");
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename)))) {
			final int startFrom = linesRead.containsKey(filename) ? linesRead.get(filename) : 0;
			final QueryParams params = new QueryParams(Query.misc_listLog);
			final String uri = filename.substring(3).replaceAll("\\d", "").toLowerCase();
			String line;
			int lines = 0;
			final List<String> omitIps = Arrays.asList(ips2skip.split(","));
			while ((line = reader.readLine()) != null) {
				if (++lines > startFrom) {
					final Matcher m = pattern.matcher(line.replaceAll("\\\\\"", ""));
					if (m.find()) {
						final String path = m.group(6);
						final Log log = new Log();
						log.setStatus(Integer.parseInt(m.group(8)));
						if (log.getStatus() < 400 &&
								((path.endsWith(".html") && !path.startsWith("/js/")) || path.endsWith(".pdf") ||
										(!path.contains(".")
												&& !path.contains("apple-app-site-association")
												&& !path.startsWith("/rest/")))) {
							log.setCreatedAt(new Timestamp(dateParser.parse(m.group(4)).getTime()));
							log.setIp(IpService.sanatizeIp(m.group(1)));
							if (!omitIps.contains(log.getIp())) {
								log.setBody(m.group(11).replace("&#39;", "'"));
								if (log.getBody().length() > 255)
									log.setBody(log.getBody().substring(0, 255));
								log.setUri(uri);
								log.setClientId(BigInteger.valueOf("afterwork".equals(uri) ? 1
										: "fanclub".equals(uri) ? 4 : "offlinepoker".equals(uri) ? 6 : 0));
								if (path.length() > 2) {
									final String[] s = path.split("\\?");
									if (s[0].length() > 1)
										log.setUri(log.getUri() + URLDecoder.decode(s[0], StandardCharsets.UTF_8));
									if (s.length > 1 && !Strings.isEmpty(s[1]))
										log.setQuery(s[1].length() > 255 ? s[1].substring(0, 252) + "..." : s[1]);
								}
								params.setSearch("log.ip='" + log.getIp() + "' and log.uri='"
										+ log.getUri().replace("'", "''")
										+ "' and log.createdAt=cast('"
										+ Instant.ofEpochMilli(log.getCreatedAt().getTime())
										+ "' as timestamp) and log.body='" + log.getBody().replace("'", "''") + "'");
								if (repository.list(params).size() == 0) {
									log.setPort(80);
									log.setMethod(m.group(5));
									if (!"-".equals(m.group(10))) {
										log.setReferer(m.group(10));
										if (log.getReferer().length() > 255)
											log.setReferer(log.getReferer().substring(0, 255));
									}
									repository.save(log);
									count++;
								}
							}
						}
					}
				}
			}
			linesRead.put(filename, lines);
		}
		return count;
	}
}
