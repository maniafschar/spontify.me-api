package com.jq.findapp.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;

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

import com.jq.findapp.api.model.Position;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationOpenTime;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.AuthenticationService.Unique;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.Ping;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@RestController
@Transactional
@CrossOrigin(origins = { Strings.URL_APP, Strings.URL_LOCALHOST, Strings.URL_LOCALHOST_TEST })
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
	private AuthenticationService authenticationService;

	@Autowired
	private ExternalService externalService;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.google.keyJS}")
	private String googleKeyJS;

	@GetMapping("unique")
	public Unique unique(String email) {
		return authenticationService.unique(email);
	}

	@PostMapping("notify")
	public void notify(String text, @RequestHeader(required = false) BigInteger user) {
		if (text != null) {
			final Integer hash = text.hashCode();
			if (!SENT_NOTIFICATIONS.contains(hash)) {
				notificationService.createTicket(TicketType.ERROR, "client", text, user);
				SENT_NOTIFICATIONS.add(hash);
			}
		}
	}

	@GetMapping("prevent/delete")
	public Map<String, String> preventDelete(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws IllegalAccessException {
		final Contact contact = authenticationService.verify(user, password, salt);
		final Map<String, String> m = marketing();
		if (m != null)
			m.put("text", Text.preventDelete.getText(contact.getLanguage()));
		return m;
	}

	@GetMapping("quotation")
	public String quotation(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws IllegalAccessException {
		authenticationService.verify(user, password, salt);
		return QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1)));
	}

	@GetMapping("marketing")
	public Map<String, String> marketing() {
		if (LocalDate.now().isAfter(LocalDate.of(2022, Month.NOVEMBER, 30)))
			return null;
		final Map<String, String> map = new HashMap<>();
		map.put("label", "50€");
		map.put("url", "https://blog.spontify.me/stats.html#marketing");
		// TODO rm0.3.0
		map.put("action", map.get("url"));
		return map;
	}

	@GetMapping("chat/{location}/{id}/{all}")
	public List<Object[]> chat(@PathVariable final boolean location, @PathVariable final BigInteger id,
			@PathVariable final boolean all, @RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setUser(authenticationService.verify(user, password, salt));
		if (location)
			params.setSearch("chat.locationId=" + id);
		else if (all)
			params.setSearch("chat.contactId=" + user + " and chat.contactId2=" + id + " or chat.contactId=" + id
					+ " and chat.contactId2=" + user);
		else
			params.setSearch("chat.seen=false and chat.contactId=" + id + " and chat.contactId2=" + user);
		final Result result = repository.list(params);
		if (!location) {
			params.setSearch("chat.seen=false and chat.contactId=" + id + " and chat.contactId2=" + user);
			final Result unseen = repository.list(params);
			if (unseen.size() > 0) {
				repository.executeUpdate(
						"update Chat chat set chat.seen=true, chat.modifiedAt=now() where (chat.seen is null or chat.seen=false) and chat.contactId="
								+ id + " and chat.contactId2=" + user);
				final Contact contact = repository.one(Contact.class, id);
				if (contact.getModifiedAt().before(new Date(Instant.now().minus(Duration.ofDays(3)).toEpochMilli())))
					notificationService.sendNotification(params.getUser(), contact,
							ContactNotificationTextType.chatSeen,
							"chat=" + user);
			}
		}
		return result.getList();
	}

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		params.setUser(authenticationService.verify(user, password, salt));
		final Map<String, Object> one = repository.one(params);
		if (one != null) {
			if (params.getQuery() == Query.contact_list) {
				final BigInteger contactId2 = (BigInteger) one.get("contact.id");
				if (!contactId2.equals(params.getUser().getId())) {
					final QueryParams params2 = new QueryParams(Query.contact_listVisit);
					params2.setUser(params.getUser());
					params2.setSearch(
							"contactVisit.contactId=" + params.getUser().getId() + " and contactVisit.contactId2="
									+ contactId2 + " and contact.id=" + contactId2);
					final Map<String, Object> visitMap = repository.one(params2);
					final ContactVisit visit;
					if (visitMap == null) {
						visit = new ContactVisit();
						visit.setContactId(params.getUser().getId());
						visit.setContactId2(contactId2);
						visit.setCount(1L);
					} else {
						visit = repository.one(ContactVisit.class, (BigInteger) visitMap.get("contactVisit.id"));
						visit.setCount(visit.getCount() + 1);
					}
					repository.save(visit);
				}
			} else if (params.getQuery() == Query.location_list) {
				final LocationVisit visit = new LocationVisit();
				visit.setLocationId((BigInteger) one.get("location.id"));
				visit.setContactId(params.getUser().getId());
				repository.save(visit);
			}
			return one;
		}
		return Collections.emptyMap();
	}

	@GetMapping("map")
	public String map(final String source, final String destination, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		return externalService.map(source, destination, authenticationService.verify(user, password, salt));
	}

	@GetMapping("google")
	public Object google(final String param, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		authenticationService.verify(user, password, salt);
		if ("js".equals(param))
			return "https://maps.googleapis.com/maps/api/js?key=" + googleKeyJS;
		if (param.startsWith("latlng=")) {
			final String[] l = param.substring(7).split(",");
			return externalService.googleAddress(Float.parseFloat(l[0]), Float.parseFloat(l[1]), user);
		}
		return externalService.google(param, user);
	}

	@GetMapping("searchLocation")
	public List<Map<String, Object>> searchLocation(String search, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		if (contact.getLongitude() == null)
			return null;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setLongitude(contact.getLongitude());
		params.setLatitude(contact.getLatitude());
		search = search.replace('\'', '_');
		params.setSearch("location.name like '%" + search + "%' or location.address like '%" + search + "%'");
		final Result result = repository.list(params);
		final List<Map<String, Object>> list = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			final Location location = repository.one(Location.class, (BigInteger) result.get(i).get("location.id"));
			final Map<String, Object> m = new HashMap<>(4);
			m.put("id", location.getId());
			m.put("name", location.getName());
			m.put("address", location.getAddress());
			list.add(m);
		}
		return list;
	}

	// TODO remove 0.3.0
	@GetMapping("nearByLocationAddress")
	public List<Map<String, Object>> nearByLocationAddress(String search, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		if (contact.getLongitude() == null)
			return null;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setLongitude(contact.getLongitude());
		params.setLatitude(contact.getLatitude());
		params.setSearch(search);
		final Result result = repository.list(params);
		final List<Map<String, Object>> list = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			final Location location = repository.one(Location.class, (BigInteger) result.get(i).get("location.id"));
			final Map<String, Object> m = new HashMap<>(4);
			m.put("id", location.getId());
			m.put("image", location.getImageList());
			m.put("name", location.getName());
			m.put("address", location.getAddress());
			list.add(m);
		}
		return list;
	}

	@PostMapping("position")
	public Map<String, Object> position(@RequestBody final Position position,
			@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		contact.setLatitude(position.getLatitude());
		contact.setLongitude(position.getLongitude());
		repository.save(contact);
		final QueryParams params = new QueryParams(Query.contact_listGeoLocationHistory);
		params.setSearch("contactGeoLocationHistory.createdAt>'" + Instant.now().minus(Duration.ofSeconds(5))
				+ "' and contactGeoLocationHistory.contactId=" + contact.getId());
		if (repository.list(params).size() == 0) {
			final GeoLocation geoLocation = externalService.googleAddress(position.getLatitude(),
					position.getLongitude(), user);
			if (geoLocation != null) {
				final Map<String, Object> result = new HashMap<>();
				if (geoLocation.getStreet() != null && geoLocation.getNumber() != null)
					result.put("street", geoLocation.getStreet() + ' ' + geoLocation.getNumber());
				else
					result.put("street", geoLocation.getStreet());
				result.put("town", geoLocation.getTown() != null ? geoLocation.getTown() : geoLocation.getCountry());
				final ContactGeoLocationHistory contactGeoLocationHistory = new ContactGeoLocationHistory();
				contactGeoLocationHistory.setContactId(contact.getId());
				contactGeoLocationHistory.setGeoLocationId(geoLocation.getId());
				contactGeoLocationHistory.setAccuracy(position.getAccuracy());
				contactGeoLocationHistory.setAltitude(position.getAltitude());
				contactGeoLocationHistory.setHeading(position.getHeading());
				contactGeoLocationHistory.setSpeed(position.getSpeed());
				repository.save(contactGeoLocationHistory);
				return result;
			}
		}
		return null;
	}

	@GetMapping("ping")
	public Ping ping(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		contact.setActive(true);
		repository.save(contact);
		return notificationService.getPingValues(contact);
	}

	@PutMapping("one")
	public void save(@RequestBody final WriteEntity entity, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt) throws Exception {
		authenticationService.verify(user, password, salt);
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
