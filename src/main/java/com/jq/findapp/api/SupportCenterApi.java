package com.jq.findapp.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
		Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> log = list.get(i);
			final String clientId = log.get("log.clientId").toString();
			if (!result.containsKey(clientId)) {
				result.put(clientId.toString(), new HashMap<>());
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
				run(importSportsBarService, "importSportsBars", list);
				run(chatService, "answerAi", list);
				run(dbService, "update", list);
				run(dbService, "cleanUpAttachments", list);
				run(engagementService, "sendRegistrationReminder", list);
				run(eventService, "findMatchingBuddies", list);
				run(eventService, "importEvents", list);
				run(eventService, "publishEvents", list);
				run(eventService, "notifyParticipation", list);
				run(importLogService, "importLog", list);
				run(rssService, "update", list);
				run(surveyService, "update", list);
				run(importLocationsService, "importImages", list);
				CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
						.map(CompletableFuture::join).collect(Collectors.toList())).join();
				run(engagementService, "sendNearBy", list).join();
				list.clear();
				run(engagementService, "sendChats", list);
				run(ipService, "lookupIps", list);
				run(sitemapService, "update", list);
				CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
						.map(CompletableFuture::join).collect(Collectors.toList())).join();
				run(dbService, "backup", list).join();
			} finally {
				schedulerRunning = false;
			}
			return null;
		});
	}

	@Async
	private CompletableFuture<Void> run(final Object bean, String method, final List<CompletableFuture<Void>> list) {
		try {
			final Cron cron = bean.getClass().getDeclaredMethod(method).getAnnotation(Cron.class);
			if (cron != null) {
				final LocalDateTime now = LocalDateTime.now();
				if (cron.minute() != now.getMinute())
					return null;
				boolean run = false;
				for (int i = 0; i < cron.hour().length; i++) {
					if (cron.hour()[i] == now.getHour()) {
						run = true;
						break;
					}
				}
				if (!run)
					return null;
			}
		} catch (Exception ex) {
			notificationService.createTicket(TicketType.ERROR, "scheduler",
					Strings.stackTraceToString(ex),
					null);
			throw new RuntimeException(ex);
		}
		list.add(CompletableFuture.supplyAsync(() -> {
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
						(log.getBody() == null ? "" : "\n" + log.getBody()));
				notificationService.createTicket(TicketType.ERROR, "scheduler",
						"uncatched exception:\n" + Strings.stackTraceToString(ex), null);
			} finally {
				log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
				if (log.getBody() != null && log.getBody().length() > 255)
					log.setBody(log.getBody().substring(0, 252) + "...");
				try {
					repository.save(log);
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
			return null;
		}));
		return list.get(list.size() - 1);
	}

	public static class SchedulerResult {
		public String result = "";
		public Exception exception;
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader final String secret) throws Exception {
		repository.one(Contact.class, BigInteger.ONE);
	}

	@Target(ElementType.METHOD)
	@Retention(RetentionPolicy.RUNTIME)
	public static @interface Cron {
		int[] hour();

		int minute() default 0;
	}
}