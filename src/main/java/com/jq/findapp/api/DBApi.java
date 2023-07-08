package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.EntityUtil;

import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("db")
public class DBApi {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader final BigInteger user) throws Exception {
		if (repository.one(Contact.class, user) == null)
			return null;
		params.setUser(repository.one(Contact.class, user));
		return repository.one(params);
	}

	@GetMapping("list")
	public List<Object[]> list(final QueryParams params, @RequestHeader final BigInteger user) throws Exception {
		if (repository.one(Contact.class, user) == null)
			return null;
		params.setUser(repository.one(Contact.class, user));
		return repository.list(params).getList();
	}

	@PutMapping("one")
	public void save(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		if (repository.one(Contact.class, user) == null || entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (checkWriteAuthorisation(e, user)) {
			if (entity.getValues().containsKey("password")) {
				final String pw = Encryption.decryptBrowser((String) entity.getValues().get("password"));
				entity.getValues().put("password", Encryption.encryptDB(pw));
				entity.getValues().put("passwordReset", BigInteger.valueOf(System.currentTimeMillis()));
			}
			EntityUtil.addImageList(entity);
			if (e.populate(entity.getValues()))
				repository.save(e);
		}
	}

	@PostMapping("one")
	public BigInteger create(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user)
			throws Exception {
		final BaseEntity e = EntityUtil.createEntity(entity, repository.one(Contact.class, user));
		repository.save(e);
		return e.getId();
	}

	@DeleteMapping("one")
	public void delete(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		if (repository.one(Contact.class, user) == null)
			return;
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (e instanceof Contact)
			notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (checkWriteAuthorisation(e, user)) {
			repository.one(Contact.class, user);
			repository.delete(e);
		}
	}

	private boolean checkWriteAuthorisation(final BaseEntity e, final BigInteger user) throws Exception {
		if (e == null)
			return false;
		if (repository.one(Contact.class, user).getType() == ContactType.demo)
			return false;
		if (e.writeAccess(user, repository))
			return true;
		notificationService.createTicket(TicketType.ERROR, "writeAuthentication",
				"Failed for " + user + " on " + e.getClass().getName() + ", id " + e.getId(), user);
		return false;
	}
}