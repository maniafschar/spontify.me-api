package com.jq.findapp.api;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
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
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.EngagementService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;

@RestController
@CrossOrigin(origins = { "https://sc.spontify.me" })
@RequestMapping("support")
public class SupportCenterApi {
	private final String baseDir = "attachments/";

	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private EngagementService engagementService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		authenticationService.deleteAccount(repository.one(Contact.class, id));
	}

	@GetMapping("user")
	public List<Object[]> user(@RequestHeader String password, @RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
		params.setUser(authenticationService.verify(adminId, password, salt));
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("feedback")
	public List<Object[]> feedback(@RequestHeader String password, @RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.contact_listFeedback);
		params.setUser(authenticationService.verify(adminId, password, salt));
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("log")
	public List<Object[]> log(String search, @RequestHeader String password, @RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setUser(authenticationService.verify(adminId, password, salt));
		params.setSearch(search);
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("chat/{id}/testrun")
	@Produces(MediaType.TEXT_PLAIN)
	public String chatTestrun(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		try (InputStream in = new FileInputStream(baseDir + "20000/" + id)) {
			final String template = IOUtils.toString(in, StandardCharsets.UTF_8);
			final Contact contact = repository.one(Contact.class, adminId);
			String s = template.replace("{{EMOJI.MISSING_FRIENDS}}", contact.getGender() == 1 ? "🕺🏻" : "💃");
			return s.replace("{{CONTACT.PSEUDONYM}}", contact.getPseudonym());
		}
	}

	@PostMapping("{id}/resend/regmail")
	public void resendRegMail(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact from = authenticationService.verify(adminId, password, salt);
		final Contact to = repository.one(Contact.class, id);
		notificationService.sendNotification(from, to, NotificationID.welcomeExt,
				"r=" + to.getLoginLink().substring(0, 10) + to.getLoginLink().substring(20));
	}

	@PostMapping("notify")
	public void notify(@RequestBody Notification data, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		for (BigInteger id : data.getIds()) {
			final Chat chat = new Chat();
			chat.setContactId(adminId);
			chat.setContactId2(id);
			chat.setNote(data.getText());
			chat.setSeen(Boolean.FALSE);
			repository.save(chat);
		}
	}

	@PutMapping("refreshDB")
	public void refreshDB(@RequestHeader String secret) throws Exception {
		if (schedulerSecret.equals(secret))
			engagementService.sendWelcomeChat();
	}
}