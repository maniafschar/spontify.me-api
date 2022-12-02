package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Block;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;

@RestController
@Transactional
@CrossOrigin(origins = { Strings.URL_APP, Strings.URL_LOCALHOST, Strings.URL_LOCALHOST_TEST })
@RequestMapping("db")
public class DBApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private NotificationService notificationService;

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws JsonMappingException, JsonProcessingException, IllegalArgumentException {
		params.setUser(authenticationService.verify(user, password, salt));
		// TODO rm 0.3.0
		if (params.getSearch() != null)
			params.setSearch(params.getSearch().replaceAll("contactBlock.contactId", "block.contactId"));
		return repository.one(params);
	}

	@GetMapping("list")
	public List<Object[]> list(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws JsonMappingException, JsonProcessingException, IllegalArgumentException {
		params.setUser(authenticationService.verify(user, password, salt));
		return repository.list(params).getList();
	}

	@PutMapping("one")
	public void save(@RequestBody final WriteEntity entity, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.verify(user, password, salt);
		if (entity.getValues().containsKey("contactId"))
			return;
		// TODO rm 0.3.0
		if ("ContactBlock".equals(entity.getClassname()))
			entity.setClassname(Block.class.getSimpleName());
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
	public BigInteger create(@RequestBody final WriteEntity entity, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.verify(user, password, salt);
		final BaseEntity e = entity.getClazz().newInstance();
		try {
			if (e.getClass().getDeclaredMethod("getContactId") != null)
				entity.getValues().put("contactId", user);
		} catch (NoSuchMethodException ex) {
			// no need to handle
		}
		EntityUtil.addImageList(entity);
		e.populate(entity.getValues());
		repository.save(e);
		return e.getId();
	}

	@DeleteMapping("one")
	public void delete(@RequestBody final WriteEntity entity, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.verify(user, password, salt);
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (e instanceof Contact)
			notificationService.createTicket(TicketType.ERROR, "Contact Deletion",
					"tried to delete contact " + e.getId(), user);
		else if (checkWriteAuthorisation(e, user))
			repository.delete(e);
	}

	private boolean checkWriteAuthorisation(BaseEntity e, BigInteger user) throws Exception {
		if (e == null)
			return false;
		if (e.writeAccess(user, repository))
			return true;
		notificationService.createTicket(TicketType.ERROR, "writeAuthentication",
				"Failed for " + user + " on " + e.getClass().getName() + ", id " + e.getId(), user);
		return false;
	}
}