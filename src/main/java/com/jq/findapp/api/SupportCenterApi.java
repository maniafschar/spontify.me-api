package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
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
	private static final List<Integer> schedulerRunning = Collections.synchronizedList(new ArrayList<>());

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
	private IpService ipService;

	@Autowired
	private MetricsEndpoint metricsEndpoint;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	private static final ExecutorService executorService = Executors.newCachedThreadPool();

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

	@PostMapping("import/location/{id}/{category}")
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

	@PostMapping("survey/test")
	public String survey() throws Exception {
		return surveyService.updateTest();
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
	public void scheduler(@RequestHeader final String secret) throws Exception {
		if (schedulerSecret.equals(secret)) {
			if (schedulerRunning.size() == 0) {
				final boolean LAST = true;
				// last job is backup, even after importLog
				run(dbService::backup, LAST);
				// importLog paralell to the rest, does not interfere
				run(importLogService::importLog, !LAST);
				run(chatService::answerAi, !LAST);
				run(dbService::update, !LAST);
				run(engagementService::sendRegistrationReminder, !LAST);
				// sendNearBy and sendChats after all event services
				// to avoid multiple chat at the same time
				run(engagementService::sendNearBy, LAST);
				run(engagementService::sendChats, LAST);
				run(ipService::lookupIps, LAST);
				run(eventService::findMatchingBuddies, !LAST);
				run(eventService::notifyParticipation, !LAST);
				run(rssService::update, !LAST);
				run(surveyService::update, !LAST);
			} else
				throw new RuntimeException("Scheduler already running " + schedulerRunning.size() + " processes");
		}
	}

	private void run(final Scheduler scheduler, final boolean last) {
		final Integer id = schedulerRunning.size() + 1;
		schedulerRunning.add(id);
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				final Log log = new Log();
				log.setContactId(BigInteger.ZERO);
				try {
					if (last) {
						boolean execute = false;
						do {
							try {
								Thread.sleep(100);
							} catch (final InterruptedException e) {
								throw new RuntimeException(e);
							}
							synchronized (schedulerRunning) {
								execute = schedulerRunning.stream().anyMatch(e -> e > id);
							}
						} while (execute);
					}
					log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
					final SchedulerResult result = scheduler.run();
					log.setUri("/support/scheduler/" + result.name);
					log.setStatus(Strings.isEmpty(result.exception) ? 200 : 500);
					if (result.result != null)
						log.setBody(result.result.trim());
					if (result.exception != null) {
						log.setBody(result.exception.getClass().getName() + ": " + result.exception.getMessage() +
								(log.getBody() == null ? "" : "\n" + log.getBody()));
						notificationService.createTicket(TicketType.ERROR, "scheduler",
								Strings.stackTraceToString(result.exception), null);
					}
				} catch (final Throwable ex) {
					log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
							(log.getBody() == null ? "" : "\n" + log.getBody()));
					notificationService.createTicket(TicketType.ERROR, "scheduler",
							"uncatched exception:\n" + Strings.stackTraceToString(ex), null);
				} finally {
					schedulerRunning.remove(id);
					log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
					if (log.getBody() != null && log.getBody().length() > 255)
						log.setBody(log.getBody().substring(0, 252) + "...");
					try {
						repository.save(log);
					} catch (final Exception e) {
						throw new RuntimeException(e);
					}
				}
			}
		});
	}

	private interface Scheduler {
		SchedulerResult run();
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
