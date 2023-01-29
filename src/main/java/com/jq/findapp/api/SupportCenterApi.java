package com.jq.findapp.api;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.transaction.Transactional;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.ImportLocationsService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.DbService;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.service.backend.ImportLogService;
import com.jq.findapp.service.backend.StatisticsService;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@RestController
@Transactional
@CrossOrigin(origins = { "https://sc.spontify.me" })
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
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			authenticationService.deleteAccount(repository.one(Contact.class, id));
		}
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
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			repository.delete(repository.one(Ticket.class, id));
		}
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

	@PostMapping("marketing")
	public void marketing(@RequestBody Map<String, Object> data, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			final List<String> ids = (List<String>) data.get("ids");
			if (ids.size() == 0) {
				final QueryParams params = new QueryParams(
						Strings.isEmpty(data.get("search")) || !((String) data.get("search")).contains("geoLocation")
								? Query.contact_listId
								: Query.contact_listByLocation);
				params.setLimit(0);
				params.setSearch((Strings.isEmpty(data.get("search")) ? "" : data.get("search") + " and ") +
						"contact.id<>" + adminId + " and contact.verified=true and contact.version is not null");
				final Result result = repository.list(params);
				for (int i = 0; i < result.size(); i++)
					ids.add(result.get(i).get("contact.id").toString());
			}
			String action = (String) data.get("action");
			if (action != null && action.startsWith("https://"))
				action = "ui.navigation.openHTML(&quot;" + action + "&quot;)";
			for (String id : ids)
				engagementService.sendChat(Text.valueOf((String) data.get("text")),
						repository.one(Contact.class, BigInteger.valueOf(Long.parseLong(id))), null, action);
		}
	}

	@PutMapping("resend/{id}")
	public void resend(@PathVariable final BigInteger id, @RequestHeader String password, @RequestHeader String salt,
			@RequestHeader String secret)
			throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			authenticationService.recoverSendEmailReminder(repository.one(Contact.class, id));
		}
	}

	@PostMapping("import/location/{id}/{category}")
	public String importLocation(@PathVariable final BigInteger id, @PathVariable final String category,
			@RequestHeader String password, @RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			return importLocationsService.importLocation(id, category);
		}
		return null;
	}

	@PutMapping("scheduler")
	public void scheduler(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret)) {
			if (schedulerRunning.size() == 0) {
				// last job is backup, even after importLog
				runLast(dbService::backup);
				// importLog paralell to the rest,does not interfere
				run(importLogService::importLog);
				run(dbService::update);
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

	private void runLast(final Scheduler run) {
		run(run, true);
	}

	private void run(final Scheduler run) {
		run(run, false);
	}

	private void run(final Scheduler run, final boolean last) {
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
					final String[] result = run.run();
					log.setUri("/support/scheduler/" + result[0]);
					log.setStatus(result[1] != null && result[1].contains("Exception") ? 500 : 200);
					if (result[1] != null)
						log.setBody(result[1].length() > 255 ? result[1].substring(0, 255) : result[1]);
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
		String[] run();
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			repository.one(Contact.class, adminId);
	}
}