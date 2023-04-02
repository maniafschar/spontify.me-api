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
import com.jq.findapp.service.ImportLocationsService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.DbService;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.service.backend.ImportLogService;
import com.jq.findapp.service.backend.IpService;
import com.jq.findapp.service.backend.StatisticsService;
import com.jq.findapp.util.Strings;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@RestController
@Transactional
@CrossOrigin(origins = { "https://sc.skillvents.com" })
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
	private DbService dbService;

	@Autowired
	private StatisticsService statisticsService;

	@Autowired
	private ImportLocationsService importLocationsService;

	@Autowired
	private ChatService chatService;

	@Autowired
	private IpService ipService;

	@Autowired
	private MetricsEndpoint metricsEndpoint;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@Value("${app.supportCenter.secret}")
	private String supportCenterSecret;

	private static final ExecutorService executorService = Executors.newCachedThreadPool();

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret) && authenticationService.verify(adminId, password, salt) != null)
			authenticationService.deleteAccount(repository.one(Contact.class, id));
	}

	@GetMapping("user")
	public List<Object[]> user(@RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@GetMapping("ticket")
	public List<Object[]> ticket(String search, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.misc_listTicket);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setSearch(search);
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@DeleteMapping("ticket/{id}")
	public void ticketDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret) && authenticationService.verify(adminId, password, salt) != null)
			repository.delete(repository.one(Ticket.class, id));
	}

	@GetMapping("log")
	public List<Object[]> log(String search, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) {
		if (supportCenterSecret.equals(secret)) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			params.setUser(authenticationService.verify(adminId, password, salt));
			params.setSearch(search);
			params.setLimit(Integer.MAX_VALUE);
			return repository.list(params).getList();
		}
		return null;
	}

	@PostMapping("email")
	@Produces(MediaType.TEXT_PLAIN)
	public void email(final BigInteger id, final String text, final String action,
			@RequestHeader String password, @RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			final Contact contact = authenticationService.verify(adminId, password, salt);
			notificationService.sendNotificationEmail(contact, repository.one(Contact.class, id), text, action);
		}
	}

	@PutMapping("resend/{id}")
	public void resend(@PathVariable final BigInteger id, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret)
			throws Exception {
		if (supportCenterSecret.equals(secret) && authenticationService.verify(adminId, password, salt) != null)
			authenticationService.recoverSendEmailReminder(repository.one(Contact.class, id));
	}

	@PostMapping("import/location/{id}/{category}")
	public String importLocation(@PathVariable final BigInteger id, @PathVariable final String category,
			@RequestHeader String password, @RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret) && authenticationService.verify(adminId, password, salt) != null)
			return importLocationsService.importLocation(id, category);
		return null;
	}

	@PostMapping("authenticate/{id}")
	public void authenticate(@PathVariable final BigInteger id, String image, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			final Contact contact = authenticationService.verify(adminId, password, salt);
			contact.setImageAuthenticate(image);
			contact.setAuthenticate(Boolean.TRUE);
			repository.save(contact);
		}
	}

	@GetMapping("metrics")
	public Map<String, Object> metrics(@RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret) && authenticationService.verify(adminId, password, salt) != null) {
			final Map<String, Object> result = new HashMap<>();
			final Set<String> names = metricsEndpoint.listNames().getNames();
			names.forEach(e -> {
				result.put(e, metricsEndpoint.metric(e, null));
			});
			return result;
		}
		return null;
	}

	@PutMapping("scheduler")
	public void scheduler(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret)) {
			if (schedulerRunning.size() == 0) {
				// last job is backup, even after importLog
				runLast(dbService::backup);
				// importLog paralell to the rest, does not interfere
				run(importLogService::importLog);
				run(chatService::answerAi);
				run(dbService::update);
				run(ipService::lookupIps);
				run(statisticsService::update);
				run(engagementService::sendRegistrationReminder);
				// sendNearBy and sendChats after all event services
				// to avoid multiple chat at the same time
				runLast(engagementService::sendNearBy);
				runLast(engagementService::sendChats);
				run(eventService::findMatchingSpontis);
				run(eventService::notifyParticipation);
				run(eventService::notifyParticipation);
			} else
				throw new RuntimeException("Scheduler already running " + schedulerRunning.size() + " processes");
		} else
			throw new RuntimeException("Scheduler secret incorrect: " + secret);
	}

	private void runLast(final Scheduler scheduler) {
		run(scheduler, true);
	}

	private void run(final Scheduler scheduler) {
		run(scheduler, false);
	}

	private void run(final Scheduler scheduler, final boolean last) {
		final Integer id = schedulerRunning.size() + 1;
		schedulerRunning.add(id);
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				if (last) {
					do {
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					} while (schedulerRunning.stream().anyMatch(e -> e > id));
				}
				final Log log = new Log();
				final long time = System.currentTimeMillis();
				try {
					log.setContactId(adminId);
					log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
					final SchedulerResult result = scheduler.run();
					log.setUri("/support/scheduler/" + result.name);
					log.setStatus(Strings.isEmpty(result.exception) ? 200 : 500);
					if (result.result != null)
						log.setBody(result.result.length() > 255 ? result.result.substring(0, 255) : result.result);
					if (result.exception != null)
						notificationService.createTicket(TicketType.ERROR, "scheduler",
								Strings.stackTraceToString(result.exception), adminId);
				} finally {
					schedulerRunning.remove(id);
					log.setTime((int) (System.currentTimeMillis() - time));
					try {
						repository.save(log);
					} catch (Exception e) {
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
		public String result;
		public Exception exception;

		public SchedulerResult(String name) {
			this.name = name;
		}
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			repository.one(Contact.class, adminId);
	}
}