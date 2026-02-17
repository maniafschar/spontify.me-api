package com.jq.findapp.api;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Entity;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

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
			if (params.getQuery() == Query.contact_list)
				params.setSearch(new SearchContact().token2clause(node, user));
			else if (params.getQuery() == Query.event_list)
				params.setSearch(new SearchEvent().token2clause(node));
			else if (params.getQuery() == Query.location_list)
				params.setSearch(new SearchLocation().token2clause(node));
		}
		return this.repository.list(params).getList();
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

	private String getRegEx(final String field, final String value) {
		return Strings.isEmpty(value) ? "1=0" : "cast(REGEXP_LIKE(" + field + ",'" + value + "') as integer)=1";
	}

	private class SearchContact {
		private String token2clause(final JsonNode node, final BigInteger contactId) {
			String search = "";
			final Contact contact = DBApi.this.repository.one(Contact.class, contactId);
			if (node.get("matches").asBoolean()) {
				final Client client = DBApi.this.repository.one(Client.class, contact.getClientId());
				search = " and (" + DBApi.this.getRegEx("contact.skills", contact.getSkills()) + " or " +
						DBApi.this.getRegEx("contact.skillsText", contact.getSkillsText()) + ')';
				if (!Strings.isEmpty(client.getSearchMandatory()) && !Strings.isEmpty(contact.getSkills()) &&
						contact.getSkillsText().contains(client.getSearchMandatory())) {
					search += " and (";
					final String[] s = contact.getSkills().split("|");
					for (var i = 0; i < s.length; i++) {
						if (s[i].indexOf(client.getSearchMandatory()) == 0)
							search += DBApi.this.getRegEx("contact.skills", s[i]) + " or ";
					}
					search = search.substring(0, search.length() - 4) + ')';
				}
				String s = this.gender(contact.getAgeMale(), 1), g = "";
				if (!Strings.isEmpty(s))
					g += " or " + s;
				s = this.gender(contact.getAgeFemale(), 2);
				if (!Strings.isEmpty(s))
					g += " or " + s;
				s = this.gender(contact.getAgeDivers(), 3);
				if (!Strings.isEmpty(s))
					g += " or " + s;
				if (!Strings.isEmpty(g))
					search += " and (" + g.substring(4) + ')';
			}

			String s = "";
			if (!Strings.isEmpty(node.get("tokens").asText())) {
				for (final String token : node.get("tokens").asText().split("\\|")) {
					if (!Strings.isEmpty(token)) {
						final String s2 = token.trim().toLowerCase();
						s += "contact.idDisplay='" + token.trim()
								+ "' or (contact.search=true and (LOWER(contact.description) like '%" + s2
								+ "%' or LOWER(contact.pseudonym) like '%" + s2
								+ "%' or LOWER(contact.skillsText) like '%" + s2 + "%')) or ";
					}
					s = s.substring(0, s.length() - 4);
				}
				if (!Strings.isEmpty(node.get("ids").asText()))
					s += (!Strings.isEmpty(s) ? " or " : "")
							+ DBApi.this.getRegEx("contact.skills", node.get("ids").asText());
				search = (s.contains("contact.idDisplay") ? "" : "contact.id<>" + contact.getClientId() + " and ")
						+ "contact.id<>" + contact.getId() + search + (Strings.isEmpty(s) ? "" : " and (" + s + ")");
			}
			return search;
		}

		private String gender(final String age, final int i) {
			if (!Strings.isEmpty(age)) {
				final String[] ageSplit = age.split(",");
				String s2 = "";
				if (Integer.parseInt(ageSplit[0]) > 18)
					s2 = "contact.age>=" + ageSplit[0];
				if (Integer.parseInt(ageSplit[1]) < 99)
					s2 += (Strings.isEmpty(s2) ? "" : " and ") + "contact.age<=" + ageSplit[1];
				return "contact.gender=" + i + (Strings.isEmpty(s2) ? "" : " and " + s2);
			}
			return "";
		}
	}

	private class SearchEvent {
		private String token2clause(final JsonNode node) {
			String s = "";
			if (!Strings.isEmpty(node.get("tokens").asText())) {
				for (final String token : node.get("tokens").asText().split("\\|")) {
					if (!Strings.isEmpty(token)) {
						final String l = ") like '%" + token.trim().toLowerCase() + "%' or LOWER(";
						s += "((contact.search=true or event.price>0) and LOWER(contact.pseudonym" + l
								+ "contact.description" + l;
						s = s.substring(0, s.lastIndexOf(" or LOWER(")) + ") or ";
						s += "LOWER(location.name" + l + "location.description" + l + "location.address" + l
								+ "location.address2" + l + "location.telephone" + l + "event.description" + l
								+ "event.skillsText" + l;
						s = s.substring(0, s.lastIndexOf(" or LOWER(")) + " or ";
					}
				}
				s = s.substring(0, s.length() - 4);
			}
			if (!Strings.isEmpty(node.get("ids").asText()))
				s += (Strings.isEmpty(s) ? "" : " or ") + DBApi.this.getRegEx("event.skills", node.get("ids").asText());
			if (node.has("bounds")) {
				final double swlat = node.get("bounds").get("southWestLat").asDouble();
				final double swlng = node.get("bounds").get("southWestLng").asDouble();
				final double nelat = node.get("bounds").get("northEastsLat").asDouble();
				final double nelng = node.get("bounds").get("northEastsLng").asDouble();
				final double borderLat = 0.1 * Math.abs(swlat - nelat);
				s += "location.latitude>" + (swlat + borderLat);
				s += " and location.latitude<" + (nelat - borderLat);
				final double borderLon = 0.1 * Math.abs(nelng - swlng);
				s += " and location.longitude>" + (swlng + borderLon);
				s += " and location.longitude<" + (nelng - borderLon);
				s += " or ";
				s += "event.latitude>" + (swlat + borderLat);
				s += " and event.latitude<" + (nelat - borderLat);
				s += " and event.longitude>" + (swlng + borderLon);
				s += " and event.longitude<" + (nelng - borderLon);
				s += ")";
			}
			if (!Strings.isEmpty(s))
				s = '(' + s + ") and ";
			return s + "event.endDate>=cast('" + Instant.now().toString().substring(0, 10)
					+ "' as timestamp)";
		}
	}

	private class SearchLocation {
		private String token2clause(final JsonNode node) {
			String s = "";
			if (!Strings.isEmpty(node.get("tokens").asText())) {
				for (final String token : node.get("tokens").asText().split("\\|")) {
					if (!Strings.isEmpty(token)) {
						final String l = ") like '%" + token.trim().toLowerCase() + "%' or LOWER(";
						s += "(LOWER(location.name" + l + "location.description" + l + "location.address" + l
								+ "location.address2" + l + "location.telephone" + l;
						s = s.substring(0, s.lastIndexOf(" or LOWER")) + ") or ";
					}
				}
				if (!Strings.isEmpty(s))
					s = '(' + s.substring(0, s.length() - 4) + ')';
			}
			if (!Strings.isEmpty(node.get("ids").asText()))
				s += (Strings.isEmpty(s) ? "" : " or ")
						+ DBApi.this.getRegEx("location.skills", node.get("ids").asText());
			if (node.get("favorites").asBoolean())
				s += (Strings.isEmpty(s) ? "" : " and ") + "locationFavorite.favorite=true";
			if (node.has("bounds")) {
				final double swlat = node.get("bounds").get("southWestLat").asDouble();
				final double swlng = node.get("bounds").get("southWestLng").asDouble();
				final double nelat = node.get("bounds").get("northEastsLat").asDouble();
				final double nelng = node.get("bounds").get("northEastsLng").asDouble();
				final double borderLat = 0.1 * Math.abs(swlat - nelat);
				s += "location.latitude>" + (swlat + borderLat);
				s += " and location.latitude<" + (nelat - borderLat);
				final double borderLon = 0.1 * Math.abs(nelng - swlng);
				s += " and location.longitude>" + (swlng + borderLon);
				s += " and location.longitude<" + (nelng - borderLon);
			}
			return s;
		}
	}
}