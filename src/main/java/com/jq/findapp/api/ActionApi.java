package com.jq.findapp.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.DBApi.WriteEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationOpenTime;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.service.NotificationService.Ping;
import com.jq.findapp.util.EntityUtil;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

@RestController
@CrossOrigin(origins = { "https://localhost", "https://findapp.online" })
@RequestMapping("action")
public class ActionApi {
	private static final List<String> QUOTATION = new ArrayList<>();
	private static final List<Integer> SENT_NOTIFICATIONS = new ArrayList<>();

	static {
		try {
			final String[] t = IOUtils
					.toString(ActionApi.class.getResourceAsStream("/quotation"), StandardCharsets.UTF_8).split("\n");
			for (String q : t) {
				q = q.trim();
				if (q.length() > 0 && !QUOTATION.contains(q))
					QUOTATION.add(q.replaceAll("\\n", "\n"));
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthenticationService authentication;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.google.keyJS}")
	private String googleKeyJS;

	@GetMapping("unique")
	public Map<String, Object> unique(String email) {
		final QueryParams params = new QueryParams(Query.contact_unique);
		params.setSearch("LOWER(contact.email)='" + email.toLowerCase() + "'");
		final Map<String, Object> result = new HashMap<>();
		result.put("email", email);
		result.put("unique", repository.one(params) == null);
		return result;
	}

	@PostMapping("notify")
	public void notify(String text) throws MessagingException {
		if (text != null) {
			final Integer hash = text.hashCode();
			if (!SENT_NOTIFICATIONS.contains(hash)) {
				notificationService.sendEmail(null, "ERROR", text);
				SENT_NOTIFICATIONS.add(hash);
			}
		}
	}

	@GetMapping("marketing")
	public Map<String, String> marketing(@RequestHeader(required = false) Long user) {
		final Map<String, String> map = new HashMap<>();
		map.put("text",
				"<b>Hol Dir das iPad 10.2</b><br>Wer bis 31.6.22 die meisten Freunde hier sammelt, bekommt ein iPad geschenkt. Mehr...");
		map.put("action", "https://blog.findapp.online");
		return map;
	}

	@GetMapping("quotation")
	public String quotation(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws IllegalAccessException {
		authentication.verify(user, password, salt);
		return QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1)));
	}

	@GetMapping("chat/{location}/{id}/{all}")
	public List<Object[]> chat(@PathVariable final boolean location, @PathVariable final BigInteger id,
			@PathVariable final boolean all, @RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setUser(authentication.verify(user, password, salt));
		if (location)
			params.setSearch("chat.locationId=" + id);
		else if (all)
			params.setSearch("chat.contactId=" + user + " and chat.contactId2=" + id + " or chat.contactId=" + id
					+ " and chat.contactId2=" + user);
		else
			params.setSearch("chat.seen=false and chat.contactId=" + id + " and chat.contactId2=" + user);
		final Result result = repository.list(params);
		if (!location)
			repository.executeUpdate(
					"update Chat chat set chat.seen=true where (chat.seen is null or chat.seen=false) and chat.contactId="
							+ id + " and chat.contactId2=" + user);
		return result.getList();
	}

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		if (!params.getQuery().name().contains("_anonymous"))
			params.setUser(authentication.verify(user, password, salt));
		final Map<String, Object> m = repository.one(params);
		if (params.getUser() != null) {
			if (params.getQuery() == Query.contact_list)
				notificationService.contactSaveVisitAndNotifyOnMatch(params.getUser(),
						(BigInteger) m.get("contact.id"));
			else if (params.getQuery() == Query.location_list) {
				final LocationVisit visit = new LocationVisit();
				visit.setLocationId((BigInteger) m.get("location.id"));
				visit.setContactId(params.getUser().getId());
				repository.save(visit);
				notificationService.locationNotifyOnMatch(params.getUser(),
						(BigInteger) m.get("location.id"), NotificationID.VisitLocation, null);
			}
		}
		return m;
	}

	@GetMapping("map")
	public String map(final String source, final String destination, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		final Contact contact = authentication.verify(user, password, salt);
		String url;
		if (source == null || source.length() == 0)
			url = "https://maps.googleapis.com/maps/api/staticmap?{destination}&markers=icon:https://findapp.online/images/mapMe{gender}.png|shadow:false|{destination}&scale=2&size=200x200&maptype=roadmap&key=";
		else {
			url = "https://maps.googleapis.com/maps/api/staticmap?{source}|{destination}&markers=icon:https://findapp.online/images/mapMe{gender}.png|shadow:false|{source}&markers=icon:https://findapp.online/images/mapLoc.png|shadow:false|{destination}&scale=2&size=600x200&maptype=roadmap&sensor=true&key=";
			url = url.replaceAll("\\{source}", source);
		}
		url = url.replaceAll("\\{destination}", destination);
		url = url.replaceAll("\\{gender}", contact.getGender() == null ? "2" : "" + contact.getGender()) + googleKey;
		final byte[] data = WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody();
		return Base64.getEncoder().encodeToString(data);
	}

	@GetMapping("google")
	public String google(final String param, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		authentication.verify(user, password, salt);
		return google(param);
	}

	private String google(String param) {
		if ("js".equals(param))
			return "https://maps.googleapis.com/maps/api/js?key=" + googleKeyJS;
		return WebClient
				.create("https://maps.googleapis.com/maps/api/" + param + (param.contains("?") ? "&" : "?") + "key="
						+ googleKey)
				.get().retrieve().toEntity(String.class).block().getBody();
	}

	@PostMapping("position")
	public Map<String, Object> position(@RequestBody final ContactGeoLocationHistory contactGeoLocationHistory,
			@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		final Contact contact = authentication.verify(user, password, salt);
		JsonNode data = new ObjectMapper()
				.readTree(google("geocode/json?latlng=" + contactGeoLocationHistory.getLatitude() + ','
						+ contactGeoLocationHistory.getLongitude()));
		final Map<String, Object> result = new HashMap<>();
		if ("OK".equals(data.get("status").asText()) && data.get("results") != null) {
			data = data.get("results").get(0).get("address_components");
			String s = "", z = "", c = "", t = "", n = "";
			for (int i = 0; i < data.size(); i++) {
				if ("".equals(s) && "route".equals(data.get(i).get("types").get(0).asText()))
					s = data.get(i).get("long_name").asText();
				else if ("".equals(n) && "street_number".equals(data.get(i).get("types").get(0).asText()))
					n = data.get(i).get("long_name").asText();
				else if ("".equals(t) &&
						("locality".equals(data.get(i).get("types").get(0).asText()) ||
								data.get(i).get("types").get(0).asText().startsWith("administrative_area_level_")))
					t = data.get(i).get("long_name").asText();
				else if ("".equals(z) && "postal_code".equals(data.get(i).get("types").get(0).asText()))
					z = data.get(i).get("long_name").asText();
				else if ("".equals(c) && "country".equals(data.get(i).get("types").get(0).asText()))
					c = data.get(i).get("short_name").asText();
			}
			if (s.length() > 0 && n.length() > 0)
				s = s + ' ' + n;
			result.put("town", t.length() > 0 ? t : c);
			result.put("street", s);
			if (contact != null) {
				contact.setLatitude(contactGeoLocationHistory.getLatitude());
				contact.setLongitude(contactGeoLocationHistory.getLongitude());
				repository.save(contact);
				contactGeoLocationHistory.setCountry(c);
				contactGeoLocationHistory.setStreet(s);
				contactGeoLocationHistory.setTown(t);
				contactGeoLocationHistory.setZipCode(z);
				repository.save(contactGeoLocationHistory);
			}
		}
		return result;
	}

	@GetMapping("ping")
	public Ping ping(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact contact = authentication.verify(user, password, salt);
		contact.setActive(true);
		repository.save(contact);
		return notificationService.getPingValues(contact);
	}

	@PutMapping("one")
	public void save(@RequestBody final WriteEntity entity, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password,
			@RequestHeader(required = false) String salt)
			throws Exception {
		authentication.verify(user, password, salt);
		final Location location = repository.one(Location.class, entity.getId());
		if (location.writeAccess(user, repository)) {
			for (int i = 0; i < entity.getValues().size(); i++) {
				if ("x".equals(entity.getValues().get("openTimes.day" + i))) {
					final String id = entity.getValues().remove("openTimes.id" + i).toString();
					if (id.length() > 0)
						repository.delete(repository.one(LocationOpenTime.class, new BigInteger(id)));
					entity.getValues().remove("openTimes.day" + i);
					entity.getValues().remove("openTimes.openAt" + i);
					entity.getValues().remove("openTimes.closeAt" + i);
				} else if (entity.getValues().get("openTimes.day" + i) != null) {
					final LocationOpenTime ot = new LocationOpenTime();
					String s = entity.getValues().remove("openTimes.id" + i).toString();
					if (s.length() > 0)
						ot.setId(new BigInteger(s));
					ot.setDay(Short.valueOf(entity.getValues().remove("openTimes.day" + i).toString()));
					s = entity.getValues().remove("openTimes.openAt" + i).toString();
					if (s.split(":").length == 2)
						s += ":00";
					if (s.contains(":"))
						ot.setOpenAt(Time.valueOf(s));
					s = entity.getValues().remove("openTimes.closeAt" + i).toString();
					if (s.split(":").length == 2)
						s += ":00";
					if (s.contains(":"))
						ot.setCloseAt(Time.valueOf(s));
					ot.setLocationId(location.getId());
					if (ot.getCloseAt() != null && ot.getOpenAt() != null)
						repository.save(ot);
				}
			}
			EntityUtil.addImageList(entity);
			location.populate(entity.getValues());
			repository.save(location);
		}
	}
}
