package com.jq.findapp.api;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Client;
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

import io.micrometer.core.instrument.util.IOUtils;
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

	private static final ExecutorService executorService = Executors.newCachedThreadPool();

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id) throws Exception {
		authenticationService.deleteAccount(repository.one(Contact.class, id));
	}

	@GetMapping("user")
	public List<Object[]> user() {
		final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
		params.setUser(repository.one(Contact.class, adminId));
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("ticket")
	public List<Object[]> ticket(final String search) {
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		params.setUser(repository.one(Contact.class, adminId));
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
		params.setUser(repository.one(Contact.class, adminId));
		params.setSearch(search);
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@PostMapping("email")
	@Produces(MediaType.TEXT_PLAIN)
	public void email(final BigInteger id, final String text, final String action) throws Exception {
		notificationService.sendNotificationEmail(repository.one(Contact.class, adminId),
				repository.one(Contact.class, id), text, action);
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
		final Contact contact = repository.one(Contact.class, adminId);
		contact.setImageAuthenticate(image);
		contact.setAuthenticate(Boolean.TRUE);
		repository.save(contact);
	}

	@PutMapping("client/{id}")
	public void client(@PathVariable final BigInteger id, final String data) throws Exception {
		final Client client = repository.one(Client.class, id);
		final JsonNode json = new ObjectMapper().readTree(data);
		boolean modified = false;
		String field = "email";
		if (json.has(field) && !json.get(field).asText().equals(client.getEmail())) {
			client.setEmail(json.get(field).asText());
			modified = true;
		}
		field = "name";
		if (json.has(field) && !json.get(field).asText().equals(client.getName())) {
			client.setName(json.get(field).asText());
			modified = true;
		}
		field = "url";
		if (json.has(field)) {
			if (!json.get(field).asText().equals(client.getUrl())) {
				client.setUrl(json.get(field).asText());
				modified = true;
			}
			String css = IOUtils.toString(new URL(client.getUrl() + "/css/style.css").openStream(),
					StandardCharsets.UTF_8);
			Matcher matcher = Pattern.compile(":root \\{([^}])*").matcher(css);
			if (matcher.find()) {
				css = matcher.group();
				matcher = Pattern.compile("--([^:].*): (.*);").matcher(css);
				css = "{";
				while (matcher.find())
					css += "\"" + matcher.group(1) + "\":\"" + matcher.group(2) + "\",";
				css = css.substring(0, css.length() - 1) + "}";
				if (!css.equals(client.getCss())) {
					client.setCss(css);
					modified = true;
				}
			}
		}
		if (modified)
			repository.save(client);
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
						} catch (final InterruptedException e) {
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
					if (!Strings.isEmpty(result.result))
						log.setBody(result.result.length() > 255 ? result.result.substring(0, 255) : result.result);
					if (result.exception != null)
						notificationService.createTicket(TicketType.ERROR, "scheduler",
								Strings.stackTraceToString(result.exception), adminId);
				} finally {
					schedulerRunning.remove(id);
					log.setTime((int) (System.currentTimeMillis() - time));
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
		if (schedulerSecret.equals(secret))
			repository.one(Contact.class, adminId);
	}
}