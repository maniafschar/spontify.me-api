package com.jq.findapp.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.jq.findapp.api.model.Marketing;
import com.jq.findapp.api.model.Position;
import com.jq.findapp.api.model.WriteEntity;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationOpenTime;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.AuthenticationService.Unique;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.service.NotificationService.Ping;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@RestController
@CrossOrigin(origins = { "https://localhost", "https://findapp.online", Strings.URL })
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
	public void notify(String text) {
		if (text != null) {
			final Integer hash = text.hashCode();
			if (!SENT_NOTIFICATIONS.contains(hash)) {
				notificationService.sendEmail(null, "ERROR", text);
				SENT_NOTIFICATIONS.add(hash);
			}
		}
	}

	@GetMapping("marketing/{language}")
	public Marketing marketing(@PathVariable final String language, @RequestHeader(required = false) BigInteger user) {
		return null;
	}

	@GetMapping("marketing/result")
	public Map<String, Object> marketingResult(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) {
		final Contact u = authenticationService.verify(user, password, salt);
		final Map<String, Object> map = new HashMap<>();
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.MONTH) < 6) {
			final QueryParams params = new QueryParams(Query.contact_marketing);
			params.setUser(u);
			params.setSearch("contactMarketing.createdAt>'2022-05-01' and contactMarketing.createdAt<'2022-07-01'");
			final List<Object[]> list = repository.list(params).getList();
			final String scoring = Text.marketing_scoring.getText(u.getLanguage());
			int columnMessage1 = 0;
			for (; columnMessage1 < list.get(0).length; columnMessage1++) {
				if ("_message1".equals(list.get(0)[columnMessage1]))
					break;
			}
			for (int i = 1; i < list.size(); i++)
				list.get(i)[columnMessage1] = scoring + list.get(i)[columnMessage1];
			map.put("text", Text.marketing_iPadText.getText(u.getLanguage()) + " "
					+ Text.marketing_list.getText(u.getLanguage()));
			map.put("action", "https://blog.spontify.me");
			map.put("list", list);
		} else
			map.put("html", Text.marketing_noActions.getText(u.getLanguage()));
		return map;
	}

	@GetMapping("quotation")
	public String quotation(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws IllegalAccessException {
		authenticationService.verify(user, password, salt);
		return QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1)));
	}

	@GetMapping("chat/{location}/{id}/{all}")
	public List<Object[]> chat(@PathVariable final boolean location, @PathVariable final BigInteger id,
			@PathVariable final boolean all, @RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) {
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
			params.setUser(authenticationService.verify(user, password, salt));
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
						(BigInteger) m.get("location.id"), NotificationID.visitLocation, null);
			}
		}
		return m;
	}

	@GetMapping("map")
	public String map(final String source, final String destination, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		String url;
		if (source == null || source.length() == 0)
			url = "https://maps.googleapis.com/maps/api/staticmap?{destination}&markers=icon:" + Strings.URL
					+ "/images/mapMe.png|shadow:false|{destination}&scale=2&size=200x200&maptype=roadmap&key=";
		else {
			url = "https://maps.googleapis.com/maps/api/staticmap?{source}|{destination}&markers=icon:" + Strings.URL
					+ "/images/mapMe.png|shadow:false|{source}&markers=icon:" + Strings.URL
					+ "/images/mapLoc.png|shadow:false|{destination}&scale=2&size=600x200&maptype=roadmap&sensor=true&key=";
			url = url.replaceAll("\\{source}", source);
		}
		url = url.replaceAll("\\{destination}", destination);
		url = url.replaceAll("\\{gender}", contact.getGender() == null ? "2" : "" + contact.getGender()) + googleKey;
		return Base64.getEncoder().encodeToString(
				WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody());
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
			return externalService.googleAddress(Float.parseFloat(l[0]), Float.parseFloat(l[1]));
		}
		return externalService.google(param);
	}

	@PostMapping("position")
	public Map<String, Object> position(@RequestBody final Position position,
			@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		final GeoLocation geoLocation = externalService.googleAddress(position.getLatitude(), position.getLongitude());
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
			contact.setLatitude(position.getLatitude());
			contact.setLongitude(position.getLongitude());
			repository.save(contact);
			return result;
		}
		return null;
	}

	@GetMapping("notifications")
	public List<Object[]> notifications(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		return notificationService.getUnreadNotifications(authenticationService.verify(user, password, salt)).getList();
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
