package com.jq.findapp.api;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Feedback;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Encryption;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = { "https://sc.findapp.online" })
@RequestMapping("support")
public class SupportCenterApi {
	private final String baseDir = "attachments/";

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthenticationService authenticationService;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.scheduler.secret}")
	private String schedulerSecret;

	@DeleteMapping("user/{id}")
	public void userDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt)
			throws Exception {
		authenticationService.verify(adminId, password, salt);
		repository.deleteAccount(id);
	}

	@GetMapping("user")
	public List<Object[]> user(@RequestHeader String password, @RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.contact_listSupportCenter);
		params.setUser(authenticationService.verify(adminId, password, salt));
		params.setLimit(Integer.MAX_VALUE);
		return repository.list(params).getList();
	}

	@GetMapping("decrypt/pw/{id}")
	public void decryptPW(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) {
		authenticationService.verify(adminId, password, salt);
		final Contact contact = repository.one(Contact.class, id);
		if (contact != null)
			System.out.println(Encryption.decryptDB(contact.getPassword()));
	}

	@GetMapping("decrypt/browser")
	public void decryptBrowser(final String text, @RequestHeader String password,
			@RequestHeader String salt) {
		authenticationService.verify(adminId, password, salt);
		System.out.println(Encryption.decryptBrowser(text));
	}

	@GetMapping("feedback")
	public List<Object[]> feedback(@RequestHeader String password, @RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.contact_listFeedback);
		params.setUser(authenticationService.verify(adminId, password, salt));
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

	@PostMapping("chat/{id}")
	public void chat(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		try (InputStream in = new FileInputStream(baseDir + "20000/" + id)) {
			final String template = IOUtils.toString(in, StandardCharsets.UTF_8);
			// final List<Contact> contacts = repository
			// .list("select c.id, c.pseudonym, c.gender from CONTACT c, ACTIVITY a "
			// + "where c.id<>3 and c.verified=1 and a.contact_id=c.id and (a.subject is
			// null or a.subject not in ('"
			// + id
			// + "')) and registered < CURDATE() - INTERVAL 3 DAY group by c.id",
			// Contact.class);
			// for (Contact contact : contacts) {
			// if (contact.getId().longValue() == 213L) {
			// String s = template.replace("{{EMOJI.MISSING_FRIENDS}}", contact.getGender()
			// == 1 ? "🕺🏻" : "💃");
			// s = s.replace("{{CONTACT.PSEUDONYM}}", contact.getPseudonym());
			// final Chat c = new Chat();
			// c.setContactId(adminId);
			// c.setContactId2(contact.getId());
			// c.setNote(s);
			// repository.save(c);
			// }
			// }
		}
	}

	@PostMapping("{id}/resend/regmail")
	public void resendRegMail(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		// final Contact to = repository.one(Contact.class, id);
		// notification.sendEmail(to, "Vielen Dank für die Registrierung auf findapp vom
		// "
		// + new SimpleDateFormat("d.M.yyyy HH:mm").format(to.getCreatedAt())
		// + ". Du hast Deine Registrierung noch nicht abgeschlossen, bitte bestätige
		// Deine Anmeldung, in dem Du auf den Link klickst.",
		// "r=" + to.getLogonLink().substring(0, 10) + to.getLogonLink().substring(20));
	}

	@PostMapping("notify")
	public void notify(@FormParam(value = "ids") final String[] ids, @FormParam(value = "text") final String text,
			@FormParam(value = "action") final String action, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		for (String id : ids) {
			// final Contact to = repository.one(Contact.class,
			// BigInteger.valueOf(Long.valueOf(id)));
			// notification.sendNotification(to, text, action, true);
		}
	}

	@DeleteMapping("feedback/{id}")
	public void feedbackDelete(@PathVariable final BigInteger id, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(adminId, password, salt);
		repository.delete(repository.one(Feedback.class, id));
	}

	@PutMapping("refreshDB")
	public void refreshDB(@RequestHeader String secret) {
		if (schedulerSecret.equals(secret)) {
			repository.executeUpdate(
					"update Contact contact set contact.age=(TO_DAYS(NOW()) - TO_DAYS(contact.birthday))/365 where contact.birthday is not null");
			repository.executeUpdate(
					"update Contact contact set rating=(select sum(rating)/count(*) from ContactRating where contactId2=contact.id)");
			repository.executeUpdate(
					"update Location location set rating=(select sum(rating)/count(*) from LocationRating where locationId=location.id)");
		}
	}
}