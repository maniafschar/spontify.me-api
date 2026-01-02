package com.jq.findapp.api;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.jq.findapp.util.Entity;
import com.jq.findapp.util.Json;

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
		params.setUser(this.repository.one(Contact.class, user));
		return this.repository.one(params);
	}

	@GetMapping("list")
	public List<Object[]> list(final QueryParams params, @RequestHeader final BigInteger user) throws Exception {
		params.setUser(this.repository.one(Contact.class, user));
		if (!Strings.isEmpty(params.getSearch()) && params.getSearch().startsWith("{")
				&& params.getSearch().endsWith("}")) {
			final JsonNode node = Json.toNode(params.getSearch());

		}
		return this.repository.list(params).getList();
	}

	// TODO rm
	@PutMapping("one")
	public void put(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		if (entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = this.repository.one(entity.getClazz(), entity.getId());
		if (this.checkWriteAuthorisation(e, user) && this.checkClient(user, e)) {
			if (entity.getValues().containsKey("password")) {
				final String pw = Encryption.decryptBrowser((String) entity.getValues().get("password"));
				entity.getValues().put("password", Encryption.encryptDB(pw));
				entity.getValues().put("passwordReset", BigInteger.valueOf(System.currentTimeMillis()));
			}
			e.populate(entity.getValues());
			this.repository.save(e);
		}
	}

	@PatchMapping("one/{id}")
	public void patch(@PathVariable final BigInteger id, @RequestBody final WriteEntity entity,
			@RequestHeader final BigInteger user) throws Exception {
		if (entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = this.repository.one(entity.getClazz(), id);
		if (this.checkWriteAuthorisation(e, user) && this.checkClient(user, e)) {
			if (entity.getValues().containsKey("password")) {
				final String pw = Encryption.decryptBrowser((String) entity.getValues().get("password"));
				entity.getValues().put("password", Encryption.encryptDB(pw));
				entity.getValues().put("passwordReset", BigInteger.valueOf(System.currentTimeMillis()));
			}
			e.populate(entity.getValues());
			this.repository.save(e);
		}
	}

	@PostMapping("one")
	public BigInteger post(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user,
			@RequestHeader final BigInteger clientId) throws Exception {
		final Contact contact = this.repository.one(Contact.class, user);
		if (clientId.equals(contact.getClientId())) {
			BaseEntity e = Entity.createEntity(entity, contact);
			try {
				this.repository.save(e);
			} catch (IllegalArgumentException ex) {
				if (ex.getMessage().startsWith("IMAGE_")) {
					entity.getValues().remove("image");
					entity.getValues().remove("imageList");
					e = Entity.createEntity(entity, contact);
					try {
						this.repository.save(e);
					} catch (final IllegalArgumentException ex2) {
						ex = ex2;
					}
				}
				if (ex.getMessage().startsWith("exists:")) {
					final BigInteger id = new BigInteger(
							ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim());
					entity.getValues().remove("contactId");
					try {
						this.patch(id, entity, user);
						return id;
					} catch (final IllegalAccessException ex2) {
						throw ex;
					}
				}
			}
			return e.getId();
		}
		throw new IllegalAccessError("clientId mismatch, should be " + contact.getClientId() + " but was " + clientId);
	}

	// TODO rm
	@DeleteMapping("one")
	public void delete(@RequestBody final WriteEntity entity, @RequestHeader final BigInteger user) throws Exception {
		final BaseEntity e = this.repository.one(entity.getClazz(), entity.getId());
		if (e instanceof Contact)
			this.notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (this.checkWriteAuthorisation(e, user) && this.checkClient(user, e))
			this.repository.delete(e);
	}

	@DeleteMapping("one/{id}")
	public void delete(@PathVariable final BigInteger id, @RequestBody final WriteEntity entity,
			@RequestHeader final BigInteger user) throws Exception {
		final BaseEntity e = this.repository.one(entity.getClazz(), id);
		if (e instanceof Contact)
			this.notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (this.checkWriteAuthorisation(e, user) && this.checkClient(user, e))
			this.repository.delete(e);
	}

	private boolean checkClient(final BigInteger user, final BaseEntity entity) {
		if (entity instanceof Location)
			return true;
		final Contact contact = this.repository.one(Contact.class, user);
		try {
			final Method clientId = entity.getClass().getMethod("getClientId");
			return clientId.invoke(entity).equals(contact.getClientId());
		} catch (final Exception ex) {
			try {
				final Method contactId = entity.getClass().getMethod("getContactId");
				final Contact contact2 = this.repository.one(Contact.class, (BigInteger) contactId.invoke(entity));
				return contact.getClientId().equals(contact2.getClientId());
			} catch (final Exception ex2) {
				throw new RuntimeException(ex2);
			}
		}
	}

	private boolean checkWriteAuthorisation(final BaseEntity e, final BigInteger user) throws Exception {
		if (e == null)
			return false;
		if (this.repository.one(Contact.class, user).getType() == ContactType.demo)
			return false;
		if (e.writeAccess(user, this.repository))
			return true;
		this.notificationService.createTicket(TicketType.ERROR, "writeAuthentication",
				"Failed on " + e.getClass().getName() + ", id " + e.getId(), user);
		throw new IllegalAccessException(
				"no write access for " + e.getClass().getSimpleName() + ", id " + e.getId() + ", user " + user);
	}
}
