package com.jq.findapp.api;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.model.Position;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactVideoCall;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.entity.LocationVisit;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.AuthenticationService.Unique;
import com.jq.findapp.service.ChatService;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.Ping;
import com.jq.findapp.service.backend.IpService;
import com.jq.findapp.util.Encryption;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

@RestController
@Transactional
@RequestMapping("action")
public class ActionApi {
	private static final List<String> QUOTATION = new ArrayList<>();

	static {
		try (final InputStream in = ActionApi.class.getResourceAsStream("/quotation")) {
			final String[] t = IOUtils
					.toString(in, StandardCharsets.UTF_8).split("\n");
			for (String q : t) {
				q = q.trim();
				if (q.length() > 0 && !QUOTATION.contains(q))
					QUOTATION.add(q.replace("\\n", "\n"));
			}
		} catch (final Exception ex) {
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
	private ChatService chatService;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private Text text;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.google.keyJS}")
	private String googleKeyJS;

	@Value("${app.paypal.key}")
	private String paypalKey;

	@Value("${app.paypal.sandbox.key}")
	private String paypalSandboxKey;

	@Value("${app.server.webSocket}")
	private String serverWebSocket;

	@GetMapping("unique")
	public Unique unique(@RequestHeader final BigInteger clientId, final String email) {
		return authenticationService.unique(clientId, email);
	}

	@PostMapping("notify")
	public void notify(final String text,
			@RequestHeader(required = false) final BigInteger user,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) {
		if (text != null)
			notificationService.createTicket(TicketType.ERROR, "client", "IP\n\t" + ip + "\n\n" + text, user);
	}

	@GetMapping("quotation")
	public String quotation(@RequestHeader final BigInteger user) {
		return QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1)));
	}

	@GetMapping("birthday")
	public Map<String, String> birthday(@RequestHeader final BigInteger user) {
		final Map<String, String> map = new HashMap<>(2);
		map.put("text", QUOTATION.get((int) (Math.random() * (QUOTATION.size() - 1))));
		map.put("image", "images/happyBirthday.png");
		return map;
	}

	@GetMapping("chat/{id}/{all}")
	public List<Object[]> chat(@PathVariable final BigInteger id,
			@PathVariable final boolean all, @RequestHeader final BigInteger user) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setUser(repository.one(Contact.class, user));
		if (all)
			params.setSearch("contactChat.contactId=" + user + " and contactChat.contactId2=" + id
					+ " or contactChat.contactId=" + id + " and contactChat.contactId2=" + user);
		else
			params.setSearch("contactChat.seen=false and contactChat.contactId=" + id
					+ " and contactChat.contactId2=" + user);
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
	public Map<String, Object> one(final QueryParams params, @RequestHeader final BigInteger clientId,
			@RequestHeader(required = false) final BigInteger user) throws Exception {
		if (user == null) {
			if (params.getQuery() != Query.location_list && params.getQuery() != Query.contact_listTeaser
					&& params.getQuery() != Query.event_listTeaser)
				throw new RuntimeException("unauthenticated request");
			final Contact contact = new Contact();
			contact.setClientId(clientId);
			contact.setId(BigInteger.ZERO);
			params.setUser(contact);
		} else
			params.setUser(repository.one(Contact.class, user));
		final Map<String, Object> one = repository.one(params);
		if (one != null) {
			if (user != null) {
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
			}
			return one;
		}
		return Collections.emptyMap();
	}

	@GetMapping("map")
	public String map(final String source, final String destination, @RequestHeader final BigInteger user)
			throws Exception {
		return externalService.map(source, destination, repository.one(Contact.class, user));
	}

	@GetMapping("google")
	public Object google(final String param, @RequestHeader final BigInteger user)
			throws Exception {
		if ("js".equals(param))
			return "https://maps.googleapis.com/maps/api/js?key=" + googleKeyJS;
		if (param.startsWith("latlng=")) {
			final String[] l = param.substring(7).split(",");
			return externalService.getAddress(Float.parseFloat(l[0]), Float.parseFloat(l[1]), true);
		}
		if (param.startsWith("town="))
			return externalService.getLatLng(param.substring(5));
		return externalService.google(param);
	}

	@GetMapping("script/{version}")
	public String script(@PathVariable final String version) throws Exception {
		return "initialisation.customElementsCss+='input-date {overflow-x:auto;max-width:100%;}';setInterval(function(){var e=ui.q('chatList');if(parseInt(e.style.height)==0&&parseInt(e.getAttribute('toggle'))<new Date().getTime()-500){var e2=document.createElement('chatList');e2.style.display='none';e.parentElement.replaceChild(e2,e);communication.ping()}},1000)";
	}

	@GetMapping("news")
	public List<Object[]> news(@RequestHeader final BigInteger clientId,
			@RequestParam(required = false) final BigInteger id,
			@RequestParam(required = false) final Float latitude,
			@RequestParam(required = false) final Float longitude) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listNews);
		final Contact contact = new Contact();
		contact.setClientId(clientId);
		params.setUser(contact);
		if (id == null) {
			if (latitude != null) {
				params.setLatitude(latitude);
				params.setLongitude(longitude);
				params.setDistance(200);
				params.setLimit(50);
			}
			params.setSearch("clientNews.publish<=cast('" + Instant.now().toString() + "' as timestamp)");
		} else
			params.setSearch("clientNews.id=" + id);
		return repository.list(params).getList();
	}

	@GetMapping("teaser/meta")
	public List<Object[]> teaserMeta(@RequestHeader final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listTeaserMeta);
		params.setLimit(0);
		params.setSearch(
				"contact.clientId=" + clientId + " and event.endDate>=cast('"
						+ Instant.now().atZone(ZoneOffset.UTC).toLocalDate() + "' as timestamp)");
		return repository.list(params).getList();
	}

	@GetMapping("teaser/{type}")
	public List<Object[]> teaser(@PathVariable final String type, @RequestParam(required = false) String search,
			@RequestHeader final BigInteger clientId, @RequestHeader(required = false) final BigInteger user,
			@RequestHeader(required = false, name = "X-Forwarded-For") final String ip) throws Exception {
		if (BigInteger.ZERO.equals(user))
			return Collections.emptyList();
		final int limit = 25;
		final QueryParams params = new QueryParams(
				"contacts".equals(type) ? Query.contact_listTeaser : Query.event_listTeaser);
		params.setDistance(1000);
		params.setLatitude(48.13684f);
		params.setLongitude(11.57685f);
		if (user == null) {
			if (ip != null) {
				final QueryParams params2 = new QueryParams(Query.misc_listIp);
				params2.setSearch("ip.ip='" + IpService.sanatizeIp(ip) + "'");
				final Result result = repository.list(params2);
				if (result.size() > 0) {
					params.setLatitude(((Number) result.get(0).get("ip.latitude")).floatValue());
					params.setLongitude(((Number) result.get(0).get("ip.longitude")).floatValue());
				} else {
					try {
						final Ip ip2 = new Ip();
						ip2.setLatitude(0f);
						ip2.setLongitude(0f);
						ip2.setIp(IpService.sanatizeIp(ip));
						repository.save(ip2);
					} catch (final PersistenceException ex) {
						// most probably added meanwhile, just continue
					}
				}
			}
			final Contact contact = new Contact();
			contact.setClientId(clientId);
			contact.setId(BigInteger.ZERO);
			params.setUser(contact);
			search = (search == null ? "" : "(" + search + ") and ") + "contact.teaser=true";
		} else {
			params.setUser(repository.one(Contact.class, user));
			if (params.getUser().getLatitude() != null) {
				params.setLatitude(params.getUser().getLatitude());
				params.setLongitude(params.getUser().getLongitude());
			}
			if (params.getQuery() == Query.contact_listTeaser)
				search = (search == null ? "" : "(" + search + ") and ") + "contact.id<>" + user;
		}
		if (params.getQuery() == Query.event_listTeaser) {
			params.setLimit(0);
			final ZonedDateTime today = Instant.now().atZone(ZoneOffset.UTC);
			search = (search == null ? "" : "(" + search + ") and ") + "(event.type='Poll' and event.startDate>=cast('"
					+ today.toLocalDateTime() + "' as timestamp) or event.type<>'Poll' and event.endDate>=cast('"
					+ today.toLocalDate() + "' as timestamp) and event.startDate<=cast('"
					+ today.plus(Duration.ofDays(15)).toLocalDate()
					+ "' as timestamp))";
		} else
			params.setLimit(limit);
		params.setSearch(search);
		return repository.list(params).getList();
	}

	@GetMapping("searchLocation")
	public List<Map<String, Object>> searchLocation(final String search, @RequestHeader final BigInteger user)
			throws Exception {
		if (Strings.isEmpty(search))
			return null;
		final Contact contact = repository.one(Contact.class, user);
		final QueryParams params = new QueryParams(Query.location_list);
		params.setUser(contact);
		params.setDistance(-1);
		params.setLatitude(
				contact.getLatitude() == null ? GeoLocationProcessor.DEFAULT_LATITUDE : contact.getLatitude());
		params.setLongitude(
				contact.getLongitude() == null ? GeoLocationProcessor.DEFAULT_LONGITUDE : contact.getLongitude());
		final String[] s = search.toLowerCase().replace('\'', '_').split(" ");
		final StringBuffer sb = new StringBuffer();
		for (int i = 0; i < s.length; i++) {
			if (!Strings.isEmpty(s[i]))
				sb.append(" and (LOWER(location.name) like '%" + s[i] + "%' or LOWER(location.address) like '%" + s[i]
						+ "%' or LOWER(location.address2) like '%" + s[i] + "%' or LOWER(location.telephone) like '%"
						+ s[i] + "%')");
		}
		params.setSearch(sb.substring(5));
		params.setLimit(50);
		final Result result = repository.list(params);
		final List<Map<String, Object>> list = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			final Map<String, Object> m = new HashMap<>(4);
			m.put("id", result.get(i).get("location.id"));
			m.put("name", result.get(i).get("location.name"));
			m.put("address", result.get(i).get("location.address"));
			list.add(m);
		}
		return list;
	}

	@PostMapping("position")
	public Map<String, Object> position(@RequestBody final Position position, @RequestHeader final BigInteger user)
			throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		contact.setLatitude(position.getLatitude());
		contact.setLongitude(position.getLongitude());
		repository.save(contact);
		final QueryParams params = new QueryParams(Query.contact_listGeoLocationHistory);
		params.setSearch("contactGeoLocationHistory.createdAt>cast('" + Instant.now().minus(Duration.ofSeconds(5))
				+ "' as timestamp) and contactGeoLocationHistory.contactId=" + contact.getId());
		if (repository.list(params).size() == 0) {
			final GeoLocation geoLocation = externalService.getAddress(position.getLatitude(), position.getLongitude(),
					false);
			if (geoLocation != null) {
				final Map<String, Object> result = new HashMap<>();
				if (geoLocation.getStreet() != null && geoLocation.getNumber() != null)
					result.put("street", geoLocation.getStreet() + ' ' + geoLocation.getNumber());
				else
					result.put("street", geoLocation.getStreet());
				result.put("town",
						geoLocation.getTown() != null ? geoLocation.getTown() : geoLocation.getCountry());
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

	@PostMapping("appointment")
	public void appointment(@RequestBody final ContactVideoCall videoCall, @RequestHeader final BigInteger user)
			throws Exception {
		final Contact contact = repository.one(Contact.class, user);
		videoCall.setContactId(user);
		repository.save(videoCall);
		final String note = text.getText(contact, TextId.notification_authenticate)
				.replace("{0}", Strings.formatDate(null, videoCall.getTime(), contact.getTimezone()));
		final ContactChat chat = new ContactChat();
		chat.setContactId(repository.one(Client.class, contact.getClientId()).getAdminId());
		chat.setContactId2(user);
		chat.setTextId(TextId.notification_authenticate);
		chat.setNote(note);
		chat.setAction("ui.startAdminCall()");
		repository.save(chat);
	}

	@PostMapping("paypal")
	public void paypal(@RequestHeader final BigInteger clientId, @RequestBody final String data) throws Exception {
		final ObjectMapper m = new ObjectMapper();
		final JsonNode n = m.readTree(data);
		notificationService.createTicket(TicketType.PAYPAL, "webhook",
				m.writerWithDefaultPrettyPrinter().writeValueAsString(n),
				repository.one(Client.class, clientId).getAdminId());
		if ("PAYMENT.CAPTURE.REFUNDED".equals(n.get("event_type").asText())) {
			String id = n.get("resource").get("links").get(1).get("href").asText();
			id = id.substring(id.lastIndexOf('/') + 1);
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.payment like '%" + id + "%'");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) result.get(i).get("eventParticipate.id"));
				eventParticipate.setState(-1);
				eventParticipate.setReason("Paypal " + n.get("resource").get("note_to_payer").asText());
				repository.save(eventParticipate);
			}
		}
	}

	@Async
	@PostMapping("videocall/{id}")
	public void videocall(@PathVariable final BigInteger id, @RequestHeader final BigInteger user) throws Exception {
		final Boolean active = WebClient.create(serverWebSocket + "active/" + id).get().retrieve()
				.toEntity(Boolean.class).block().getBody();
		if ((active == null || !active) && chatService.isVideoCallAllowed(repository.one(Contact.class, user), id))
			notificationService.sendNotificationSync(repository.one(Contact.class, user),
					repository.one(Contact.class, id),
					ContactNotificationTextType.contactVideoCall, "video=" + user);
	}

	@GetMapping("paypalKey")
	public Map<String, Object> paypalKey(final BigInteger id, final String publicKey,
			@RequestHeader(required = false) final BigInteger user) throws Exception {
		final int fee = 20;
		final Map<String, Object> paypalConfig = new HashMap<>(3);
		paypalConfig.put("fee", fee);
		if (user != null && !BigInteger.ZERO.equals(user)) {
			final Contact contact = repository.one(Contact.class, user);
			final String s = getPaypalKey(user);
			paypalConfig.put("key", s.substring(0, s.indexOf(':')));
			paypalConfig.put("currency", "EUR");
			if (id != null)
				paypalConfig.put("email", Encryption.encrypt(repository.one(Contact.class, id).getEmail(), publicKey));
			if (contact.getFee() != null && contact.getFeeDate() != null && contact.getFee().intValue() != fee) {
				paypalConfig.put("fee", contact.getFee());
				paypalConfig.put("feeDate", contact.getFeeDate());
				paypalConfig.put("feeAfter", fee);
			}
		}
		return paypalConfig;
	}

	@GetMapping("version")
	public String version(@RequestHeader final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_maxAppVersion);
		params.setSearch("contact.clientId=" + clientId);
		final Result result = repository.list(params);
		return result.size() > 0 ? (String) result.get(0).get("_c") : "";
	}

	@GetMapping("ping")
	public Ping ping(@RequestHeader final BigInteger user) throws Exception {
		return notificationService.getPingValues(repository.one(Contact.class, user));
	}

	private boolean isPaypalSandbox(final BigInteger user) {
		final QueryParams params = new QueryParams(Query.misc_setting);
		params.setSearch("setting.label='paypal.sandbox'");
		final Map<String, Object> settings = repository.one(params);
		return settings != null && ("," + settings.get("setting.data") + ",").contains("," + user + ",");
	}

	private String getPaypalKey(final BigInteger user) {
		return isPaypalSandbox(user) ? paypalSandboxKey : paypalKey;
	}
}
