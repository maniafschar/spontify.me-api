package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
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

	@PostMapping("location/search/{query}")
	public String importLocation(@PathVariable final String query) throws Exception {
		return importLocationsService.searchLocation(query);
	}

	@PostMapping("authenticate/{id}")
	public void authenticate(@PathVariable final BigInteger id, final String image) throws Exception {
		final Contact contact = repository.one(Contact.class, id);
		contact.setImageAuthenticate(image);
		contact.setAuthenticate(Boolean.TRUE);
		repository.save(contact);
	}

	@GetMapping("report")
	public Map<String, Map<Date, Integer>> metrics() throws Exception {
		final Map<String, Map<String, Set<String>>> result = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setLimit(Integer.MAX_VALUE);
		params.setSearch("log.uri='/action/teaser/contacts' and log.createdAt<cast('" + Instant.now().minus(Duration.ofDays(40)) + "' as timestamp)");
		result.put("login", new HashMap<>());
		result.put("anonym", new HashMap<>());
		Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			final String key = Instant.ofEpochMilli(((Date) row.get("log.createdAt")).getTime()).toString().substring(0, 10);
			if (row.get("log.contactId") == null) {
				if (!result.get("anonym").containsKey(key))
					result.get("anonym").put(key, new HashSet<>());
				result.get("anonym").get(key).add(row.get("log.ip"));
			} else {
				if (!result.get("login").containsKey(key))
					result.get("login").put(key, new HashSet<>());
				result.get("login").get(key).add(row.get("log.contactId").toString());
			}
		}
		params.setSearch(params.getSearch().replace("uri='/action/teaser/contacts", " not like '/%") + " and LOWER(ip.org) not like '%google%' and LOWER(ip.org) not like '%facebook%'"");
		result.put("teaser", new HashMap<>());
		Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
		}
		return result;
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

	@Async
	@PutMapping("scheduler")
	public void scheduler(@RequestHeader final String secret) throws Exception {
		if (schedulerSecret.equals(secret)) {
			if (schedulerRunning)
				throw new RuntimeException("Failed to start, scheduler is currently running");
			schedulerRunning = true;
			final List<CompletableFuture<Void>> list = new ArrayList<>();
			list.add(run(chatService::answerAi));
			list.add(run(dbService::update));
			list.add(run(engagementService::sendRegistrationReminder));
			list.add(run(eventService::findMatchingBuddies));
			list.add(run(eventService::importEvents));
			list.add(run(eventService::notifyParticipation));
			list.add(run(importLogService::importLog));
			list.add(run(rssService::update));
			list.add(run(surveyService::update));
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(engagementService::sendNearBy).join();
			list.clear();
			list.add(run(engagementService::sendChats));
			list.add(run(ipService::lookupIps));
			list.add(run(sitemapService::update));
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(dbService::backup).join();
			schedulerRunning = false;
		}
	}

	@Async
	private CompletableFuture<Void> run(final Supplier<SchedulerResult> scheduler) {
		return CompletableFuture.supplyAsync(() -> {
			final Log log = new Log();
			log.setContactId(BigInteger.ZERO);
			try {
				log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
				final SchedulerResult result = scheduler.get();
				log.setUri("/support/scheduler/" + result.name);
				log.setStatus(Strings.isEmpty(result.exception) ? 200 : 500);
				if (result.result != null)
					log.setBody(result.result.trim());
				if (result.exception != null) {
					log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
							+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
					notificationService.createTicket(TicketType.ERROR, "scheduler",
							Strings.stackTraceToString(result.exception), null);
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
		});
	}

	public static class SchedulerResult {
		private final String name;
		public String result = "";
		public Exception exception;

		public SchedulerResult(final String name) {
			this.name = name;
		}
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader final String secret) throws Exception {
		repository.one(Contact.class, BigInteger.ONE);
	}
}
