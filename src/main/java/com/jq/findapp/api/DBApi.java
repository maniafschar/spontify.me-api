package com.jq.findapp.api;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.EntityUtil;

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

@RestController
@CrossOrigin(origins = { "https://localhost", "https://findapp.online" })
@RequestMapping("db")
public class DBApi {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authentication;

	@Autowired
	private NotificationService notificationService;

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		if (!params.getQuery().name().contains("_anonymous"))
			params.setUser(authentication.verify(user, password, salt));
		return repository.one(params);
	}

	@GetMapping("list")
	public List<Object[]> list(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		if (!params.getQuery().name().contains("_anonymous"))
			params.setUser(authentication.verify(user, password, salt));
		return repository.list(params).getList();
	}

	@PutMapping("one")
	public void save(@RequestBody final WriteEntity entity, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws Exception {
		authentication.verify(user, password, salt);
		if (entity.getValues().containsKey("contactId"))
			return;
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (checkWriteAuthorisation(e, user)) {
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
	public BigInteger create(@RequestBody final WriteEntity entity, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws Exception {
		authentication.verify(user, password, salt);
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
	public void delete(@RequestBody final WriteEntity entity, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws Exception {
		authentication.verify(user, password, salt);
		final BaseEntity e = repository.one(entity.getClazz(), entity.getId());
		if (checkWriteAuthorisation(e, user))
			repository.delete(e);
	}

	private boolean checkWriteAuthorisation(BaseEntity e, BigInteger user) throws Exception {
		if (e == null)
			return false;
		if (e.writeAccess(user, repository))
			return true;
		notificationService.sendEmail(null, "ERROR writeAuthentication",
				"Failed for " + user + " on " + e.getClass().getName() + ", id " + e.getId());
		return false;
	}

	public static class WriteEntity {
		private BigInteger id;
		private String classname;
		private Map<String, Object> values;

		public BigInteger getId() {
			return id;
		}

		public void setId(BigInteger id) {
			this.id = id;
		}

		public Class<BaseEntity> getClazz() {
			try {
				return (Class<BaseEntity>) Class.forName(BaseEntity.class.getPackage().getName() + "." + classname);
			} catch (ClassNotFoundException ex) {
				throw new RuntimeException(ex);
			}
		}

		public String getClassname() {
			return classname;
		}

		public void setClassname(String classname) {
			this.classname = classname;
		}

		public Map<String, Object> getValues() {
			return values;
		}

		public void setValues(Map<String, Object> values) {
			this.values = values;
		}

		@Override
		public String toString() {
			return classname + (id == null ? "" : ": " + id) + (values == null ? "" : " " + values.keySet());
		}
	}
}