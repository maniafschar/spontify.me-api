package com.jq.findapp.api;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;
import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.Position;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.AuthenticationService.Unique;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.Ping;
import com.jq.findapp.util.Strings;

@RestController
@Transactional
@CrossOrigin(origins = { Strings.URL_APP, Strings.URL_APP_NEW, Strings.URL_LOCALHOST, Strings.URL_LOCALHOST_TEST })
@RequestMapping("action")
public class ActionApi {
	private static final List<String> QUOTATION = new ArrayList<>();

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

	@Value("${app.eventbrite.key}")
	private String eventbriteKey;

	@Value("${app.paypal.key}")
	private String paypalKey;

	@Value("${app.paypal.sandbox.key}")
	private String paypalSandboxKey;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.url}")
	private String url;

	@Value("${app.url.lookupip}")
	private String lookupIp;

	private final String paypalUrl = "https://api-m.paypal.com/";
	private final String paypalSandboxUrl = "https://api-m.sandbox.paypal.com/";

	@GetMapping("unique")
	public Unique unique(String email) {
		return authenticationService.unique(email);
	}

	@PostMapping("notify")
	public void notify(String text, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false, name = "X-Forwarded-For") String ip) {
		if (text != null)
			notificationService.createTicket(TicketType.ERROR, "client", "IP\n\t" + ip + "\n\n" + text, user);
	}

	@GetMapping("quotation")
	public String quotation(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) {
		authenticationService.verify(user, password, salt);
		return QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1)));
	}

	@GetMapping("chat/{id}/{all}")
	public List<Object[]> chat(@PathVariable final BigInteger id,
			@PathVariable final boolean all, @RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setUser(authenticationService.verify(user, password, salt));
		if (all)
			params.setSearch("contactChat.contactId=" + user + " and contactChat.contactId2=" + id
					+ " or contactChat.contactId=" + id
					+ " and contactChat.contactId2=" + user);
		else
			params.setSearch(
					"contactChat.seen=false and contactChat.contactId=" + id + " and contactChat.contactId2=" + user);
		final Result result = repository.list(params);
		params.setSearch(
				"contactChat.seen=false and contactChat.contactId=" + id + " and contactChat.contactId2=" + user);
		final Result unseen = repository.list(params);
		if (unseen.size() > 0) {
			repository.executeUpdate(
					"update ContactChat contactChat set contactChat.seen=true, contactChat.modifiedAt=now() where (contactChat.seen is null or contactChat.seen=false) and contactChat.contactId="
							+ id + " and contactChat.contactId2=" + user);
			final Contact contact = repository.one(Contact.class, id);
			if (contact.getModifiedAt().before(new Date(Instant.now().minus(Duration.ofDays(3)).toEpochMilli())))
				notificationService.sendNotification(params.getUser(), contact,
						ContactNotificationTextType.chatSeen, "chat=" + user);
		}
		return result.getList();
	}

	@GetMapping("one")
	public Map<String, Object> one(final QueryParams params, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		if (user == null) {
			if (params.getQuery() != Query.contact_listTeaser && params.getQuery() != Query.event_listTeaser)
				throw new RuntimeException("unauthenticated request");
		} else
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
			return externalService.getAddress(Float.parseFloat(l[0]), Float.parseFloat(l[1]), user);
		}
		if (param.startsWith("town="))
			return externalService.getLatLng(param.substring(5), user);
		return externalService.google(param, user);
	}

	@GetMapping("teaser/{type}")
	public List<Object[]> teaser(@PathVariable final String type, @RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt,
			@RequestHeader(required = false, name = "X-Forwarded-For") String ip) throws Exception {
		final QueryParams params = new QueryParams(
				"contacts".equals(type) ? Query.contact_listTeaser : Query.event_listTeaser);
		params.setLimit(20);
		params.setDistance(100000);
		params.setLatitude(48.13684f);
		params.setLongitude(11.57685f);
		if (user == null) {
			if (ip != null) {
				final QueryParams params2 = new QueryParams(Query.misc_listIp);
				params2.setSearch("ip.ip='" + ip + "'");
				final Result result = repository.list(params2);
				if (result.size() > 0) {
					params.setLatitude(((Number) result.get(0).get("ip.latitude")).floatValue());
					params.setLongitude(((Number) result.get(0).get("ip.longitude")).floatValue());
				} else {
					final String json = WebClient
							.create(lookupIp.replace("{ip}", ip)).get()
							.retrieve().toEntity(String.class).block().getBody();
					final Ip ip2 = new ObjectMapper()
							.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
							.readValue(json, Ip.class);
					final String location = new ObjectMapper().readTree(json).get("loc").asText();
					ip2.setLatitude(Float.parseFloat(location.split(",")[0]));
					ip2.setLongitude(Float.parseFloat(location.split(",")[1]));
					try {
						repository.save(ip2);
					} catch (PersistenceException ex) {
						// most probably added meanwhile, just continue
					}
					params.setLatitude(ip2.getLatitude());
					params.setLongitude(ip2.getLongitude());
				}
			}
			params.setSearch("contact.teaser=true");
		} else {
			params.setUser(authenticationService.verify(user, password, salt));
			if (params.getUser().getLatitude() != null) {
				params.setLatitude(params.getUser().getLatitude());
				params.setLongitude(params.getUser().getLongitude());
			}
			if (params.getQuery() == Query.contact_listTeaser)
				params.setSearch("contact.id<>" + user);
			else
				params.setSearch("event.endDate>='" + Instant.now().atZone(ZoneOffset.UTC).toLocalDate() + "'");
		}
		return repository.list(params).getList();
	}

	@GetMapping("searchLocation")
	public List<Map<String, Object>> searchLocation(String search, @RequestHeader BigInteger user,
			@RequestHeader String password, @RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		if (Strings.isEmpty(search))
			return null;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setLatitude(
				contact.getLatitude() == null ? GeoLocationProcessor.DEFAULT_LATITUDE : contact.getLatitude());
		params.setLongitude(
				contact.getLongitude() == null ? GeoLocationProcessor.DEFAULT_LONGITUDE : contact.getLongitude());
		final String[] s = search.replace('\'', '_').split(" ");
		final StringBuffer sb = new StringBuffer();
		for (int i = 0; i < s.length; i++) {
			if (!Strings.isEmpty(s[i]))
				sb.append(" and (location.name like '%" + s[i] + "%' or location.address like '%" + s[i]
						+ "%' or location.address2 like '%" + s[i] + "%' or location.telephone like '%" + s[i] + "%')");
		}
		params.setSearch(sb.substring(5));
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
			final GeoLocation geoLocation = externalService.getAddress(position.getLatitude(),
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
				contactGeoLocationHistory.setManual(position.isManual());
				repository.save(contactGeoLocationHistory);
				return result;
			}
		}
		return null;
	}

	@PostMapping("eventbrite")
	public void eventbrite(@RequestBody final Map<String, Object> data) throws Exception {
		String data2 = "";
		if (!((String) data.get("api_url")).contains("{api-endpoint-to-fetch-object-details}"))
			data2 = "\n\n" + WebClient.create(data.get("api_url") + "?token=" + eventbriteKey)
					.get().retrieve().toEntity(String.class).block().getBody();
		notificationService.createTicket(TicketType.ERROR, "eventbrite", data + data2, BigInteger.valueOf(3));
	}

	@PostMapping("paypal")
	public void paypal(@RequestBody final String data) throws Exception {
		final ObjectMapper m = new ObjectMapper();
		final JsonNode n = m.readTree(data);
		notificationService.createTicket(TicketType.PAYPAL, "webhook",
				m.writerWithDefaultPrettyPrinter().writeValueAsString(n), BigInteger.valueOf(3));
		if ("PAYMENT.CAPTURE.REFUNDED".equals(n.get("event_type").asText())) {
			String id = n.get("resource").get("links").get(1).get("href").asText();
			id = id.substring(id.lastIndexOf('/') + 1);
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.payment like '%" + id + "%'");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) result.get(i).get("eventParticipate.id"));
				eventParticipate.setState((short) -1);
				eventParticipate.setReason("Paypal " + n.get("resource").get("note_to_payer").asText());
				repository.save(eventParticipate);
			}
		}
	}

	@GetMapping("paypalKey")
	public Map<String, Object> paypalKey(@RequestHeader(required = false) BigInteger user,
			@RequestHeader(required = false) String password, @RequestHeader(required = false) String salt)
			throws Exception {
		final int fee = 20;
		final Map<String, Object> paypalConfig = new HashMap<>(3);
		paypalConfig.put("fee", fee);
		if (user != null) {
			final Contact contact = authenticationService.verify(user, password, salt);
			final String s = getPaypalKey(user);
			paypalConfig.put("key", s.substring(0, s.indexOf(':')));
			paypalConfig.put("currency", "EUR");
			paypalConfig.put("fee", contact.getFee() == null ? fee : contact.getFee());
			if (contact.getFeeDate() != null && ((Number) paypalConfig.get("fee")).intValue() != fee) {
				paypalConfig.put("feeDate", contact.getFeeDate());
				paypalConfig.put("feeAfter", fee);
			}
		}
		return paypalConfig;
	}

	@PutMapping("paypalRegister")
	public Map<String, Object> paypalRegister(BigInteger merchantId, String merchantIdInPayPal,
			boolean permissionsGranted, boolean consentStatus, String accountStatus, String riskStatus,
			@RequestHeader BigInteger user, @RequestHeader String password, @RequestHeader String salt)
			throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		if (user.equals(merchantId) && permissionsGranted && consentStatus) {
			contact.setPaypalMerchantId(merchantIdInPayPal);
			repository.save(contact);
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setSearch("contact.id=" + user);
			return repository.one(params);
		}
		notificationService.createTicket(TicketType.PAYPAL, "failed register",
				"user: " + merchantId + "\npaypal merchant id: " + merchantIdInPayPal + "\npermissionsGranted: "
						+ permissionsGranted + "\nconsentStatus: " + consentStatus + "\naccountStatus: " + accountStatus
						+ "\nriskStatus:" + riskStatus,
				user);
		return null;
	}

	@GetMapping("paypalSignUpSellerUrl")
	public String paypalSignUpSellerUrl(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		authenticationService.verify(user, password, salt);
		final String ppUrl = getPaypalUrl(user);
		JsonNode n = new ObjectMapper().readTree(WebClient.create(ppUrl + "v1/oauth2/token")
				.post().accept(MediaType.APPLICATION_JSON)
				.header("User-Agent", "curl/7.55.1")
				.header("Authorization",
						"Basic " + Base64.getEncoder().encodeToString((getPaypalKey(user)).getBytes()))
				.bodyValue("grant_type=client_credentials")
				.retrieve().toEntity(String.class).block().getBody());
		System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(n));
		n = new ObjectMapper().readTree(WebClient.create(ppUrl + "v2/customer/partner-referrals")
				.post().contentType(MediaType.APPLICATION_JSON)
				.header("User-Agent", "curl/7.55.1")
				.header("Authorization", "Bearer " + n.get("access_token").asText())
				.bodyValue(IOUtils.toString(getClass().getResourceAsStream("/template/paypalSignUpSeller.json"),
						StandardCharsets.UTF_8).replace("{trackingId}", "" + user).replace("{returnUrl}", url))
				.retrieve().toEntity(String.class).block().getBody());
		return n.get("links").get(1).get("href").asText();
	}

	@GetMapping("ping")
	public Ping ping(@RequestHeader BigInteger user, @RequestHeader String password,
			@RequestHeader String salt) throws Exception {
		final Contact contact = authenticationService.verify(user, password, salt);
		contact.setActive(true);
		repository.save(contact);
		return notificationService.getPingValues(contact);
	}

	private boolean isPaypalSandbox(BigInteger user) {
		final QueryParams params = new QueryParams(Query.misc_setting);
		params.setSearch("setting.label='paypal.sandbox'");
		final Map<String, Object> settings = repository.one(params);
		return settings != null && ("," + settings.get("setting.value") + ",").contains("," + user + ",");
	}

	private String getPaypalKey(BigInteger user) {
		return isPaypalSandbox(user) ? paypalSandboxKey : paypalKey;
	}

	private String getPaypalUrl(BigInteger user) {
		return isPaypalSandbox(user) ? paypalSandboxUrl : paypalUrl;
	}
}