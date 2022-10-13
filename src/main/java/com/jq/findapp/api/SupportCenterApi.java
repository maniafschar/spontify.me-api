package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;

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

import com.jq.findapp.api.model.Notification;
import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.WhatToDoService;
import com.jq.findapp.service.backend.DbUpdateService;
import com.jq.findapp.service.backend.EngagementService;
import com.jq.findapp.service.backend.EventService;
import com.jq.findapp.service.backend.ImportLogService;
import com.jq.findapp.service.backend.StatisticsService;

@RestController
@Transactional
@CrossOrigin(origins = { "https://sc.spontify.me" })
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
	private WhatToDoService whatToDoService;

	@Autowired
	private DbUpdateService dbUpdateService;

	@Autowired
	private StatisticsService statisticsService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@Value("${app.supportCenter.secret}")
	private String supportCenterSecret;

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

	@PostMapping("chat")
	public void chat(@RequestBody Notification data, @RequestHeader String password,
			@RequestHeader String salt, @RequestHeader String secret) throws Exception {
		if (supportCenterSecret.equals(secret)) {
			authenticationService.verify(adminId, password, salt);
			for (BigInteger id : data.getIds()) {
				final Chat chat = new Chat();
				chat.setContactId(adminId);
				chat.setContactId2(id);
				chat.setNote(data.getText());
				chat.setSeen(Boolean.FALSE);
				repository.save(chat);
			}
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

	@PutMapping("scheduler")
	public void scheduler(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret)) {
			dbUpdateService.update();
			engagementService.sendSpontifyEmail();
			engagementService.sendRegistrationReminder();
			engagementService.sendChats();
			engagementService.sendNearBy();
			whatToDoService.findAndNotify();
			eventService.findAndNotify();
			eventService.notifyParticipation();
			importLogService.importLog();
			statisticsService.update();
		}
	}

	@GetMapping("healthcheck")
	public void healthcheck(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			repository.one(Contact.class, adminId);
	}
}