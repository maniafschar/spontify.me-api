package com.jq.findapp.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Log.LogStatus;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.ChatService;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.DbService;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.service.backend.ImportLocationsService;
import com.jq.findapp.service.backend.ImportLogService;
import com.jq.findapp.service.backend.ImportSportsBarService;
import com.jq.findapp.service.backend.IpService;
import com.jq.findapp.service.backend.MarketingLocationService;
import com.jq.findapp.service.backend.MarketingService;
import com.jq.findapp.service.backend.RssService;
import com.jq.findapp.service.backend.SitemapService;
import com.jq.findapp.service.backend.SurveyService;
import com.jq.findapp.util.LogFilter;
import com.jq.findapp.util.Strings;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@RestController
@Transactional
@CrossOrigin(origins = { "https://sc.skills.community" })
@RequestMapping("support")
public class SupportCenterApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private EngagementService engagementService;

	@Autowired
	private ImportLogService importLogService;

	@Autowired
	private EventService eventService;

	@Autowired
	private RssService rssService;

	@Autowired
	private SurveyService surveyService;

	@Autowired
	private DbService dbService;

	@Autowired
	private ImportLocationsService importLocationsService;

	@Autowired
	private ImportSportsBarService importSportsBarService;

	@Autowired
	private ChatService chatService;

	@Autowired
	private SitemapService sitemapService;

	@Autowired
	private IpService ipService;

	@Autowired
	private MarketingService marketingService;

	@Autowired
	private MarketingLocationService marketingLocationService;

	@Autowired
	private MetricsEndpoint metricsEndpoint;

	@Value("${app.admin.buildScript}")
	private String buildScript;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	private static final Set<String> running = new HashSet<>();
	private ZonedDateTime now;

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id) throws Exception {
		authenticationService.deleteAccount(repository.one(Contact.class, id));
	}

	@GetMapping("user")
	public List<Object[]> user() {
		final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
		params.setLimit(0);
		return repository.list(params).getList();
	}

	@GetMapping("ticket")
	public List<Object[]> ticket(final String search) {
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		params.setSearch(search);
		params.setLimit(0);
		return repository.list(params).getList();
	}

	@DeleteMapping("ticket/{id}")
	public void ticketDelete(@PathVariable final BigInteger id) throws Exception {
		repository.delete(repository.one(Ticket.class, id));
	}

	@GetMapping("log")
	public List<Object[]> log(final String search) {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setSearch(search);
		params.setLimit(0);
		return repository.list(params).getList();
	}

	@GetMapping("marketing/{id}")
	public Map<String, List<Object[]>> marketing(@PathVariable final BigInteger id) {
		final Map<String, List<Object[]>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.contact_listMarketing);
		params.setSearch("contactMarketing.clientMarketingId=" + id);
		params.setLimit(0);
		final Result contactMarketing = repository.list(params);
		result.put("contactMarketing", contactMarketing.getList());
		params.setQuery(Query.misc_listMarketing);
		params.setSearch("clientMarketing.id=" + id);
		result.put("clientMarketing", repository.list(params).getList());
		params.setQuery(Query.misc_listLog);
		params.setSearch("log.uri='/marketing' and log.query like 'm=" + id + "&%' and log.createdAt>=cast('"
				+ Instant.ofEpochMilli(repository.one(ClientMarketing.class, id).getStartDate().getTime())
				+ "' as timestamp)");
		final Result log = repository.list(params);
		final Pattern locationIdPattern = Pattern.compile("\"locationId\":.?\"(\\d+)\"", Pattern.MULTILINE);
		final List<String> processed = new ArrayList<>();
		for (int i = 0; i < contactMarketing.size(); i++) {
			final Matcher matcher = locationIdPattern
					.matcher((String) contactMarketing.get(i).get("contactMarketing.storage"));
			if (matcher.find())
				processed.add(matcher.group(1));
		}
		final int length = log.getList().get(0).length;
		final List<Object[]> logs = new ArrayList<>();
		final Object[] header = Arrays.copyOf(log.getList().get(0), length + 2);
		header[length] = "log.name";
		header[length + 1] = "log.address";
		logs.add(header);
		for (int i = 0; i < log.size(); i++) {
			final String locationId = ((String) log.get(i).get("log.query")).split("&")[1].substring(2);
			if (!processed.contains(locationId)) {
				final Object[] row = Arrays.copyOf(log.getList().get(i + 1), length + 2);
				final Location location = repository.one(Location.class, new BigInteger(locationId));
				row[length] = location.getName();
				row[length + 1] = location.getAddress();
				logs.add(row);
				processed.add(locationId);
			}
		}
		result.put("log", logs);
		return result;
	}

	@PostMapping("email")
	@Produces(MediaType.TEXT_PLAIN)
	public void email(final BigInteger id, final String text, final String action) throws Exception {
		notificationService.sendNotificationEmail(null, repository.one(Contact.class, id), text, action);
	}

	@PutMapping("resend/{id}")
	public void resend(@PathVariable final BigInteger id) throws Exception {
		authenticationService.recoverSendEmailReminder(repository.one(Contact.class, id));
	}

	@PostMapping("location/import/{id}/{category}")
	public String importLocation(@PathVariable final BigInteger id, @PathVariable final String category)
			throws Exception {
		return importLocationsService.importLocation(id, category);
	}

	@PostMapping("authenticate/{id}")
	public void authenticate(@PathVariable final BigInteger id, final String image) throws Exception {
		final Contact contact = repository.one(Contact.class, id);
		contact.setImageAuthenticate(image);
		contact.setAuthenticate(Boolean.TRUE);
		repository.save(contact);
	}

	@PostMapping("run/{classname}")
	public SchedulerResult run(@PathVariable final String classname) throws Exception {
		final String[] s = classname.split("\\.");
		final Object clazz = getClass().getDeclaredField(s[0]).get(this);
		final SchedulerResult result = (SchedulerResult) clazz.getClass()
				.getDeclaredMethod("run" + (s.length > 1 ? s[1] : ""))
				.invoke(clazz);
		LogFilter.body.set(result.toString());
		return result;
	}

	@GetMapping("report/{days}")
	public Map<String, Map<String, Map<String, Set<String>>>> report(@PathVariable final int days) throws Exception {
		final Map<String, Map<String, Map<String, Set<String>>>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_listLog);
		final String anonym = "anonym", login = "login", teaser = "teaser";
		params.setLimit(0);
		params.setSearch(
				"log.clientId>0 and (log.uri='/action/teaser/contacts' or log.uri not like '/%') and LOWER(ip.org) not like '%google%' and LOWER(ip.org) not like '%facebook%' and LOWER(ip.org) not like '%amazon%' and log.createdAt>cast('"
						+ Instant.now().minus(Duration.ofDays(days)) + "' as timestamp)");
		final Result list = repository.list(params);
		final Set<String> users = new HashSet<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			final String clientId = log.get("log.clientId").toString();
			if (!result.containsKey(clientId)) {
				result.put(clientId, new HashMap<>());
				result.get(clientId).put(anonym, new HashMap<>());
				result.get(clientId).put(login, new HashMap<>());
				result.get(clientId).put(teaser, new HashMap<>());
			}
			if (((String) log.get("log.uri")).startsWith("/") && log.get("log.contactId") != null
					&& !BigInteger.ZERO.equals(log.get("log.contactId"))) {
				final String day = new SimpleDateFormat("-yyyy-MM-dd").format(log.get("log.createdAt"));
				if (!users.contains(log.get("log.contactId") + day)) {
					addLogEntry(result.get(clientId), login, log);
					users.add(log.get("log.contactId") + day);
				}
			}
		}
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			if (((String) log.get("log.uri")).startsWith("/")
					&& (log.get("log.contactId") == null || BigInteger.ZERO.equals(log.get("log.contactId"))))
				addLogEntry(result.get(log.get("log.clientId").toString()), anonym, log, login);
		}
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			if (!((String) log.get("log.uri")).startsWith("/"))
				addLogEntry(result.get(log.get("log.clientId").toString()), teaser, log, login, anonym);
		}
		return result;
	}

	@GetMapping("report/{days}/api")
	public Map<String, Map<String, BigInteger>> reportApi(@PathVariable final int days) throws Exception {
		final Map<String, Map<String, BigInteger>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_listLog);
		final String time = "time", count = "count";
		params.setLimit(0);
		params.setSearch(
				"log.clientId>0 and log.uri like '/%' and log.uri not like '/support/%' and log.uri not like '/marketing/%' and log.uri not like '/ws/%' and log.createdAt>cast('"
						+ Instant.now().minus(Duration.ofDays(days)) + "' as timestamp)");
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			final String id = log.get("log.uri") + "|" + log.get("log.webCall");
			final Map<String, BigInteger> map;
			if (result.containsKey(id))
				map = result.get(id);
			else {
				map = new HashMap<>();
				map.put(time, BigInteger.ZERO);
				map.put(count, BigInteger.ZERO);
				result.put(id, map);
			}
			map.put(time, map.get(time).add(BigInteger.valueOf((Integer) log.get("log.time"))));
			map.put(count, map.get(count).add(BigInteger.ONE));
		}
		return result;
	}

	@PostMapping("build/{type}")
	public Object build(@PathVariable final String type) throws Exception {
		if ("state".equals(type))
			return metrics();
		if ("status".equals(type)) {
			final ProcessBuilder pb = new ProcessBuilder("/usr/bin/bash", "-c", "ps aux|grep java");
			pb.redirectErrorStream(true);
			return IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
		}
		if ("server".equals(type) || "sc".equals(type)) {
			final ProcessBuilder pb = new ProcessBuilder(buildScript.replace("{type}", type).split(" "));
			pb.redirectErrorStream(true);
			return IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8);
		}
		if (type.startsWith("client|")) {
			String result = "";
			for (final String client : type.substring(7).split(",")) {
				final ProcessBuilder pb = new ProcessBuilder(
						(buildScript.replace("{type}", "client") + " " + client).split(" "));
				pb.redirectErrorStream(true);
				result += IOUtils.toString(pb.start().getInputStream(), StandardCharsets.UTF_8) + "\n\n";
			}
			return result.trim();
		}
		return null;
	}

	private boolean addLogEntry(final Map<String, Map<String, Set<String>>> map, final String type,
			final Map<String, Object> log, String... check) {
		final String key = Instant.ofEpochMilli(((Timestamp) log.get("log.createdAt")).getTime()).toString()
				.substring(0, 10);
		final String value = log.get("log.ip").toString();
		if (check != null) {
			for (final String t : check)
				if (map.get(t).containsKey(key) && map.get(t).get(key).contains(value))
					return false;
		}
		if (!map.get(type).containsKey(key))
			map.get(type).put(key, new HashSet<>());
		map.get(type).get(key).add(value);
		return true;
	}

	@GetMapping("metrics")
	public Map<String, Object> metrics() throws Exception {
		final Map<String, Object> result = new HashMap<>();
		final Set<String> names = metricsEndpoint.listNames().getNames();
		names.forEach(e -> {
			result.put(e, metricsEndpoint.metric(e, null));
		});
		return result;
	}

	@PutMapping("scheduler")
	public synchronized void scheduler(@RequestHeader final String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			run();
	}

	@Async
	private void run() {
		CompletableFuture.supplyAsync(() -> {
			now = Instant.now().atZone(ZoneId.of("Europe/Berlin"));
			final List<CompletableFuture<Void>> list = new ArrayList<>();
			run(importSportsBarService, null, list, "0 3");
			run(chatService, null, list, null);
			run(marketingLocationService, null, list, "* 6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21");
			run(marketingLocationService, "Sent", list, "10 19");
			run(marketingLocationService, "Unfinished", list, "30 17");
			run(marketingLocationService, "Cooperation", list, "40 17");
			run(dbService, null, list, null);
			run(dbService, "CleanUp", list, "30 0");
			run(engagementService, "Registration", list, "40 10");
			run(eventService, "Match", list, null);
			run(eventService, "MatchDays", list, "20 9");
			run(eventService, "Import", list, new "40 5");
			run(eventService, "Publish", list, null);
			run(eventService, "Participation", list, null);
			run(eventService, "Series", list, "40 23");
			run(importLogService, null, list, null);
			run(rssService, null, list, null);
			run(surveyService, null, list, null);
			run(importLocationsService, null, list, "50 0");
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			list.clear();
			run(marketingService, null, list, null);
			run(marketingService, "Result", list, null);
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(engagementService, "NearBy", null, null);
			list.clear();
			run(engagementService, null, list, null);
			run(ipService, null, list, null);
			run(sitemapService, null, list, "0 20");
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(dbService, "Backup", null, null);
			return null;
		});
	}

	@Async
	private void run(final Object bean, final String method, final List<CompletableFuture<Void>> list, final String cron) {
		if (!cron(cron))
			return;
		final CompletableFuture<Void> e = CompletableFuture.supplyAsync(() -> {
			final Log log = new Log();
			log.setContactId(BigInteger.ZERO);
			log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
			final String m = "run" + (method == null ? "" : method);
			String name = bean.getClass().getSimpleName();
			if (name.contains("$"))
				name = name.substring(0, name.indexOf('$'));
			log.setUri("/support/scheduler/" + name + "/" + m);
			try {
				if (running.contains(name + '.' + m))
					log.setStatus(LogStatus.Running);
				else {
					running.add(name + '.' + m);
					final SchedulerResult result = (SchedulerResult) bean.getClass().getMethod(m).invoke(bean);
					log.setStatus(Strings.isEmpty(result.exception) ? LogStatus.Ok : LogStatus.Error);
					if (result.body != null)
						log.setBody(result.body.trim());
					if (result.exception != null) {
						log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
								+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
						notificationService.createTicket(TicketType.ERROR, "scheduler",
								(result.body == null ? "" : result.body + "\n")
										+ Strings.stackTraceToString(result.exception),
								null);
					}
				}
			} catch (final Throwable ex) {
				log.setStatus(LogStatus.Exception);
				log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
						(Strings.isEmpty(log.getBody()) ? "" : "\n" + log.getBody()));
				notificationService.createTicket(TicketType.ERROR, "scheduler",
						"uncaught exception:\n" + Strings.stackTraceToString(ex)
								+ (Strings.isEmpty(log.getBody()) ? "" : "\n\n" + log.getBody()),
						null);
			} finally {
				running.remove(name + '.' + m);
				log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
				if (log.getBody() != null && log.getBody().length() > 255)
					log.setBody(log.getBody().substring(0, 252) + "...");
				try {
					repository.save(log);
				} catch (final Exception e2) {
					throw new RuntimeException(e2);
				}
			}
			return null;
		});
		if (list == null)
			e.join();
		else
			list.add(e);
	}

	/**
	 * 1 2 3 4 5
	 * ┬ ┬ ┬ ┬ ┬
	 * │ │ │ │ │
	 * │ │ │ │ └──── Weekday (0-7, Sunday is 0 or 7)
	 * │ │ │ └────── Month (1-12)
	 * │ │ └──────── Day (1-31)
	 * │ └────────── Hour (0-23)
	 * └──────────── Minute (0-59)
	 */
	boolean cron(final String cron) {
		if (cron == null)
			return true;
		if (Strings.isEmpty(cron))
			return false;
		final String[] s = (cron.trim() + " * * * *").split(" ");
		return match(s[0], now.getMinute())
				&& match(s[1], now.getHour())
				&& match(s[2], now.getDayOfMonth())
				&& match(s[3], now.getMonth())
				&& match(s[4], now.getDayOfWeek());
	}

	private boolean match(final String field, final int value) {
		if ("*".equals(field))
			return true;
		final String[] s = field.split(",");
		for (int i = 0; i < s.length; i++) {
			if (s[i].contains("/")) {
				if (value % Integer.parseInt(s[i].split("/")[1]) == 0)
					return true;
			} else if (Integer.parseInt(s[i]) == value)
				return true;
		}
		return false;
	}

	public static class SchedulerResult {
		public String body = "";
		public Exception exception;

		@Override
		public String toString() {
			return (body == null ? "" : body)
					+ (exception == null ? "" : "\n" + Strings.stackTraceToString(exception)).trim();
		}
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader final String secret) throws Exception {
		repository.one(Contact.class, BigInteger.ONE);
	}
}
