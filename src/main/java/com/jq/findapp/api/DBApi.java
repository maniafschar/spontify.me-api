package com.jq.findapp.api;

import java.lang.reflect.Method;
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
import com.jq.findapp.entity.Location;
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
	public Map<String, Object> get(final QueryParams params, @RequestHeader final BigInteger user) throws Exception {
		params.setUser(repository.one(Contact.class, user));
		return repository.one(params);
	}

	@GetMapping("list")
	public List<Object[]> list(final QueryParams params, @RequestHeader final BigInteger user) throws Exception {
		params.setUser(repository.one(Contact.class, user));
		return repository.list(params).getList();
	}

	// TODO rm
	@PutMapping("one")
	public void put(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		if (entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (checkWriteAuthorisation(e, user) && checkClient(user, e)) {
			if (entity.getValues().containsKey("password")) {
				final String pw = Encryption.decryptBrowser((String) entity.getValues().get("password"));
				entity.getValues().put("password", Encryption.encryptDB(pw));
				entity.getValues().put("passwordReset", BigInteger.valueOf(System.currentTimeMillis()));
			}
			EntityUtil.addImageList(entity);
			e.populate(entity.getValues());
			repository.save(e);
		}
	}

	@PatchMapping("one/{id}")
	public void patch(@PathVariable final BigInteger id, @RequestBody final WriteEntity entity,
			@RequestHeader final BigInteger user) throws Exception {
		if (entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = repository.one(entity.getClazz(), id);
		if (checkWriteAuthorisation(e, user) && checkClient(user, e)) {
			if (entity.getValues().containsKey("password")) {
				final String pw = Encryption.decryptBrowser((String) entity.getValues().get("password"));
				entity.getValues().put("password", Encryption.encryptDB(pw));
				entity.getValues().put("passwordReset", BigInteger.valueOf(System.currentTimeMillis()));
			}
			EntityUtil.addImageList(entity);
			e.populate(entity.getValues());
			repository.save(e);
		}
	}

	@PostMapping("one")
	public BigInteger post(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user,
			@RequestHeader final BigInteger clientId) throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		if (clientId.equals(contact.getClientId())) {
			final BaseEntity e = EntityUtil.createEntity(entity, contact);
			repository.save(e);
			return e.getId();
		}
		throw new IllegalAccessError("clientId mismatch, should be " + contact.getClientId() + " but was " + clientId);
	}

	// TODO rm
	@DeleteMapping("one")
	public void delete(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (e instanceof Contact)
			notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (checkWriteAuthorisation(e, user) && checkClient(user, e))
			repository.delete(e);
	}

	@DeleteMapping("one/{id}")
	public void delete(@PathVariable final BigInteger id, @RequestBody final WriteEntity entity,
			@RequestHeader final BigInteger user) throws Exception {
		final BaseEntity e = repository.one(entity.getClazz(), id);
		if (e instanceof Contact)
			notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (checkWriteAuthorisation(e, user) && checkClient(user, e))
			repository.delete(e);
	}

	private boolean checkClient(final BigInteger user, final BaseEntity entity) {
		if (entity instanceof Location)
			return true;
		final Contact contact = repository.one(Contact.class, user);
		try {
			final Method clientId = entity.getClass().getMethod("getClientId");
			return clientId.invoke(entity).equals(contact.getClientId());
		} catch (final Exception ex) {
			try {
				final Method contactId = entity.getClass().getMethod("getContactId");
				final Contact contact2 = repository.one(Contact.class, (BigInteger) contactId.invoke(entity));
				return contact.getClientId().equals(contact2.getClientId());
			} catch (final Exception ex2) {
				throw new RuntimeException(ex2);
			}
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
