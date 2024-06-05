package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Log;
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
import com.jq.findapp.service.backend.LocationMarketingService;
import com.jq.findapp.service.backend.RssService;
import com.jq.findapp.service.backend.SitemapService;
import com.jq.findapp.service.backend.SurveyService;
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
	private LocationMarketingService locationMarketingService;

	@Autowired
	private MetricsEndpoint metricsEndpoint;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	private static volatile boolean schedulerRunning = false;

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id) throws Exception {
		authenticationService.deleteAccount(repository.one(Contact.class, id));
	}

	@GetMapping("user")
	public List<Object[]> user() {
		final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("ticket")
	public List<Object[]> ticket(final String search) {
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		params.setSearch(search);
		params.setLimit(Integer.MAX_VALUE);
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
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
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

	@PostMapping("run/{classname}/{methodname}")
	public SchedulerResult run(@PathVariable final String classname, @PathVariable final String methodname)
			throws Exception {
		final Object clazz = getClass().getDeclaredField(classname).get(this);
		return (SchedulerResult) clazz.getClass().getDeclaredMethod(methodname).invoke(clazz);
	}

	@GetMapping("report/{days}")
	public Map<String, Map<String, Map<String, Set<String>>>> report(@PathVariable final int days)
			throws Exception {
		final Map<String, Map<String, Map<String, Set<String>>>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_listLog);
		final String anonym = "anonym", login = "login", teaser = "teaser";
		params.setLimit(Integer.MAX_VALUE);
		params.setSearch(
				"log.clientId>0 and (log.uri='/action/teaser/contacts' or log.uri not like '/%') and LOWER(ip.org) not like '%google%' and LOWER(ip.org) not like '%facebook%' and LOWER(ip.org) not like '%amazon%' and log.createdAt>cast('"
						+ Instant.now().minus(Duration.ofDays(days)) + "' as timestamp)");
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			final String clientId = log.get("log.clientId").toString();
			if (!result.containsKey(clientId)) {
				result.put(clientId, new HashMap<>());
				result.get(clientId).put(anonym, new HashMap<>());
				result.get(clientId).put(login, new HashMap<>());
				result.get(clientId).put(teaser, new HashMap<>());
			}
			final String key = Instant.ofEpochMilli(((Timestamp) log.get("log.createdAt")).getTime()).toString()
					.substring(0, 10);
			if (((String) log.get("log.uri")).startsWith("/")) {
				if (log.get("log.contactId") == null)
					addLogEntry(result.get(clientId).get(anonym), key, (String) log.get("log.ip"));
				else if (!BigInteger.ZERO.equals(log.get("log.contactId")))
					addLogEntry(result.get(clientId).get(login), key, log.get("log.contactId").toString());
			} else
				addLogEntry(result.get(clientId).get(teaser), key, (String) log.get("log.ip"));
		}
		return result;
	}

	@GetMapping("report/{days}/api")
	public Map<String, Map<String, BigInteger>> reportApi(@PathVariable final int days)
			throws Exception {
		final Map<String, Map<String, BigInteger>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_listLog);
		final String time = "time", count = "count";
		params.setLimit(Integer.MAX_VALUE);
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
			map.put(time, map.get(time).add((BigInteger) log.get("log.time")));
			map.put(count, map.get(count).add(BigInteger.ONE));
		}
		return result;
	}

	private void addLogEntry(final Map<String, Set<String>> map, final String key,
			final String value) {
		if (!map.containsKey(key))
			map.put(key, new HashSet<>());
		map.get(key).add(value);
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
		if (schedulerSecret.equals(secret)) {
			if (schedulerRunning)
				throw new RuntimeException("Failed to start, scheduler is currently running");
			run();
		}
	}

	@Async
	private CompletableFuture<Void> run() {
		return CompletableFuture.supplyAsync(() -> {
			try {
				schedulerRunning = true;
				final List<CompletableFuture<Void>> list = new ArrayList<>();
				run(importSportsBarService, "importSportsBars", list, new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 }, 0);
				run(chatService, "answerAi", list, null, 0);
				run(dbService, "update", list, null, 0);
				run(dbService, "cleanUpAttachments", list, new int[] { 0 }, 30);
				run(engagementService, "sendRegistrationReminder", list, new int[] { 0 }, 40);
				run(eventService, "findMatchingBuddies", list, null, 0);
				run(eventService, "importEvents", list, new int[] { 5 }, 40);
				run(eventService, "publishEvents", list, null, 0);
				run(eventService, "notifyParticipation", list, null, 0);
				run(importLogService, "importLog", list, null, 0);
				run(rssService, "update", list, null, 0);
				run(surveyService, "update", list, null, 0);
				// run(importLocationsService, "importImages", list, null, 0);
				CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
						.map(CompletableFuture::join).collect(Collectors.toList())).join();
				run(engagementService, "sendNearBy", null, null, 0);
				list.clear();
				run(engagementService, "sendChats", list, null, 0);
				run(ipService, "lookupIps", list, null, 0);
				run(sitemapService, "update", list, new int[] { 20 }, 0);
				CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
						.map(CompletableFuture::join).collect(Collectors.toList())).join();
				run(dbService, "backup", null, null, 0);
			} finally {
				schedulerRunning = false;
			}
			return null;
		});
	}

	@Async
	private void run(final Object bean, String method, final List<CompletableFuture<Void>> list,
			int[] hours, int minute) {
		if (hours != null) {
			final LocalDateTime now = LocalDateTime.now();
			if (minute != now.getMinute() || !Arrays.stream(hours).anyMatch(e -> e == now.getHour()))
				return;
		}
		final CompletableFuture<Void> e = CompletableFuture.supplyAsync(() -> {
			final Log log = new Log();
			log.setContactId(BigInteger.ZERO);
			try {
				log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
				final SchedulerResult result = (SchedulerResult) bean.getClass().getMethod(method).invoke(bean);
				String name = bean.getClass().getSimpleName();
				if (name.contains("$"))
					name = name.substring(0, name.indexOf('$'));
				log.setUri("/support/scheduler/" + name + "/" + method);
				log.setStatus(Strings.isEmpty(result.exception) ? 200 : 500);
				if (result.result != null)
					log.setBody(result.result.trim());
				if (result.exception != null) {
					log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
							+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
					notificationService.createTicket(TicketType.ERROR, "scheduler",
							(result.result == null ? "" : result.result + "\n")
									+ Strings.stackTraceToString(result.exception),
							null);
				}
			} catch (final Throwable ex) {
				log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
						(Strings.isEmpty(log.getBody()) ? "" : "\n" + log.getBody()));
				notificationService.createTicket(TicketType.ERROR, "scheduler",
						"uncaught exception:\n" + Strings.stackTraceToString(ex)
								+ (Strings.isEmpty(log.getBody()) ? "" : "\n\n" + log.getBody()),
						null);
			} finally {
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

	public static class SchedulerResult {
		public String result = "";
		public Exception exception;
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader final String secret) throws Exception {
		repository.one(Contact.class, BigInteger.ONE);
	}
}
