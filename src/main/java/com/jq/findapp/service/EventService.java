package com.jq.findapp.service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.EventParticipate;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class EventService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private Text text;

	@Autowired
	private ImportMunich importMunich;

	public SchedulerResult findMatchingBuddies() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/findMatchingBuddies");
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
							"(length(contact.skills)>0 or length(contact.skillsText)>0))");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.event_listMatching);
			params.setDistance(50);
			params.setSearch(
					"event.startDate>=current_timestamp and TO_DAYS(event.startDate)-1<=TO_DAYS(current_timestamp)");
			final LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				params.setUser(repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")));
				params.setLatitude(params.getUser().getLatitude());
				params.setLongitude(params.getUser().getLongitude());
				final Result events = repository.list(params);
				for (int i2 = 0; i2 < events.size(); i2++) {
					final Event event = repository.one(Event.class, (BigInteger) events.get(i2).get("event.id"));
					if (!params.getUser().getId().equals(event.getContactId())) {
						final LocalDateTime realDate = getRealDate(event, now);
						final Contact contactEvent = repository.one(Contact.class, event.getContactId());
						if (realDate.isAfter(now)
								&& realDate.minusDays(1).isBefore(now)
								&& !isMaxParticipants(event, realDate, params.getUser())
								&& Score.getContact(contactEvent, params.getUser()) > 0.4) {
							final ZonedDateTime t = realDate
									.atZone(TimeZone.getTimeZone(params.getUser().getTimezone()).toZoneId());
							final String time = t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute();
							if (event.getLocationId() == null)
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotifyWithoutLocation,
										Strings.encodeParam("e=" + event.getId() + "_" + realDate.toLocalDate()),
										time,
										event.getDescription());
							else
								notificationService.sendNotification(contactEvent,
										params.getUser(), ContactNotificationTextType.eventNotify,
										Strings.encodeParam("e=" + event.getId() + "_" + realDate.toLocalDate()),
										(realDate.getDayOfYear() == now.getDayOfYear()
												? text.getText(params.getUser(), TextId.today)
												: text.getText(params.getUser(), TextId.tomorrow)) + " " + time,
										(String) events.get(i2).get("location.name"));
							count++;
							break;
						}
					}
				}
			}
			result.result = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult notifyParticipation() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/notifyParticipation");
		try {
			final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
			params.setSearch("eventParticipate.state=1 and eventParticipate.eventDate>'"
					+ Instant.now().minus((Duration.ofDays(1)))
					+ "' and eventParticipate.eventDate<'"
					+ Instant.now().plus(Duration.ofDays(1)) + "'");
			params.setLimit(0);
			final Result ids = repository.list(params);
			final ZonedDateTime now = Instant.now().atZone(ZoneOffset.UTC);
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				final EventParticipate eventParticipate = repository.one(EventParticipate.class,
						(BigInteger) ids.get(i).get("eventParticipate.id"));
				final Event event = repository.one(Event.class, eventParticipate.getEventId());
				if (event == null)
					repository.delete(eventParticipate);
				else {
					final ZonedDateTime time = Instant.ofEpochMilli(event.getStartDate().getTime())
							.atZone(ZoneOffset.UTC);
					if (time.getDayOfMonth() == now.getDayOfMonth() &&
							(time.getHour() == 0
									|| time.getHour() > now.getHour() && time.getHour() < now.getHour() + 3)) {
						if (event.getLocationId() != null && event.getLocationId().compareTo(BigInteger.ZERO) > 0) {
							if (repository.one(Location.class, event.getLocationId()) == null)
								repository.delete(event);
							else {
								final Contact contact = repository.one(Contact.class, eventParticipate.getContactId());
								final ZonedDateTime t = event.getStartDate().toInstant()
										.atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId());
								notificationService.sendNotification(
										repository.one(Contact.class, event.getContactId()),
										contact, ContactNotificationTextType.eventNotification,
										Strings.encodeParam(
												"e=" + event.getId() + "_" + eventParticipate.getEventDate()),
										repository.one(Location.class, event.getLocationId()).getName(),
										t.getHour() + ":" + (t.getMinute() < 10 ? "0" : "") + t.getMinute());
								count++;
							}
						}
					}
				}
			}
			result.result = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public LocalDateTime getRealDate(final Event event) {
		return getRealDate(event, LocalDateTime.now(ZoneId.systemDefault()));
	}

	public void publish(final BigInteger id) throws Exception {
		final Event event = repository.one(Event.class, id);
		final Contact contact = repository.one(Contact.class, event.getContactId());
		final Location location = repository.one(Location.class, event.getLocationId());
		final SimpleDateFormat df = new SimpleDateFormat("d.M.yyyy H:mm");
		externalService.publishOnFacebook(contact.getClientId(),
				df.format(event.getStartDate()) + "\n" + location.getName() + "\n" + location.getAddress() + "\n\n"
						+ event.getDescription(),
				"/rest/action/marketing/event/" + id);
	}

	private LocalDateTime getRealDate(final Event event, final LocalDateTime now) {
		LocalDateTime realDate = Instant.ofEpochMilli(event.getStartDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime();
		if (!"o".equals(event.getRepetition())) {
			while (realDate.isBefore(now)) {
				if ("w1".equals(event.getRepetition()))
					realDate = realDate.plusWeeks(1);
				else if ("w2".equals(event.getRepetition()))
					realDate = realDate.plusWeeks(2);
				else if ("m".equals(event.getRepetition()))
					realDate = realDate.plusMonths(1);
				else
					realDate = realDate.plusYears(1);
			}
		}
		return realDate;
	}

	private boolean isMaxParticipants(final Event event, final LocalDateTime date, final Contact contact) {
		if (event.getMaxParticipants() == null)
			return false;
		final QueryParams params = new QueryParams(Query.event_listParticipateRaw);
		params.setUser(contact);
		params.setSearch("eventParticipate.eventId=" + event.getId() + " and eventParticipate.eventDate='"
				+ date.toLocalDate() + "' and eventParticipate.state=1");
		return repository.list(params).size() >= event.getMaxParticipants().intValue();
	}

	public SchedulerResult importEvents() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importEvents");
		try {
			result.result = importMunich.run(this);
		} catch (Exception e) {
			result.exception = e;
		}
		return result;
	}

	protected String get(final String url) {
		try {
			return IOUtils.toString(new URL(url).openStream(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Component
	private static class ImportMunich {
		@Autowired
		private Repository repository;

		@Value("${app.event.munich.baseUrl}")
		private String url;

		private final Pattern regexAddress = Pattern.compile("itemprop=\"address\"(.*?)</svg>(.*?)</a>");
		private final Pattern regexAddressRef = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		private final Pattern regexDesc = Pattern.compile(" itemprop=\"description\">(.*?)</p>");
		private final Pattern regexAddressExternal = Pattern.compile("class=\"anfahrt\"(.*?)data-venue=\"(.*?)\"");
		private final Pattern regexAddressRefExternal = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		private final Pattern regexDescExternal = Pattern.compile(" itemprop=\"description\">(.*?)</div>");
		private final Pattern regexImage = Pattern.compile("<picture(.*?)<source srcset=\"(.*?) ");
		private final Pattern regexName = Pattern.compile("itemprop=\"location\"(.*?)</svg>(.*?)</");
		private final QueryParams params = new QueryParams(Query.location_listId);
		private final long offset = ZonedDateTime.now(ZoneId.of("Europe/Berlin")).getOffset().getTotalSeconds() * 1000;
		private Client client;
		private EventService eventService;

		String run(EventService eventService) throws Exception {
			this.eventService = eventService;
			client = repository.one(Client.class, BigInteger.ONE);
			params.setUser(repository.one(Contact.class, client.getAdminId()));
			int count = page(eventService.get(url + "/veranstaltungen/event"));
			// page 2: count += page(urlRetriever.get(url + "/veranstaltungen/event"));
			params.setQuery(Query.event_list);
			params.setSearch(
					"event.startDate>'" + Instant.now().plus(Duration.ofHours(6)) + "' and event.startDate<'"
							+ Instant.now().plus(Duration.ofHours(30))
							+ "' and event.url is not null and (event.repetition is null or event.repetition='o') and event.maxParticipants is null and location.zipCode like '8%' and location.country='DE'");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++)
				eventService.publish((BigInteger) result.get(i).get("event.id"));
			return "Munich: " + count;
		}

		private int page(String page) throws Exception {
			int count = 0;
			page = page.substring(page.indexOf("<ul class=\"m-listing__list\""));
			page = page.substring(0, page.indexOf("</ul>") + 5);
			if (page.length() > 40) {
				final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
						.parse(new InputSource(new StringReader(page)));
				final NodeList lis = doc.getElementsByTagName("li");
				for (int i = 0; i < lis.getLength(); i++) {
					final Node node = lis.item(i).getFirstChild().getChildNodes().item(1);
					final Event event = new Event();
					event.setContactId(client.getAdminId());
					event.setSkillsText(node.getChildNodes().item(1).getTextContent().trim());
					event.setDescription(node.getFirstChild().getFirstChild().getTextContent().trim());
					boolean externalPage = lis.item(i).getFirstChild().getChildNodes().item(2) != null;
					if (externalPage) {
						event.setUrl(
								lis.item(i).getFirstChild().getChildNodes().item(2).getFirstChild().getNextSibling()
										.getAttributes().getNamedItem("href").getNodeValue().trim());
					} else if (node.getFirstChild().getFirstChild().getAttributes() != null)
						event.setUrl(url + node.getFirstChild().getFirstChild()
								.getAttributes().getNamedItem("href").getNodeValue().trim());
					final Date date = new SimpleDateFormat("dd.MM.yyyy' - 'HH:mm")
							.parse(node.getChildNodes().item(2).getChildNodes().item(3)
									.getAttributes().getNamedItem("datetime").getNodeValue());
					event.setStartDate(new Timestamp(date.getTime() - offset));
					params.setQuery(Query.event_list);
					params.setSearch(
							"event.startDate='" + event.getStartDate() + "' and event.url='" + event.getUrl() + "'");
					if (repository.list(params).size() == 0 && !Strings.isEmpty(event.getUrl())
							&& !event.getUrl().contains("eventim")) {
						page = eventService.get(event.getUrl());
						event.setDescription(event.getDescription()
								+ ("\n\n" + getField(externalPage ? regexDescExternal : regexDesc, page, 1))
										.trim());
						final Location location = new Location();
						if (externalPage) {
							location.setUrl(getField(regexAddressRefExternal, page, 2));
							final String[] s = getField(regexAddressExternal, page, 2).split(",");
							location.setAddress(
									s[s.length - 1].trim() + "\n" + s[s.length - 1].trim().replace('+', ' '));
							location.setName(s[0] + (s.length > 3 ? ", " + s[1] : ""));
						} else {
							final String image = getField(regexImage, page, 2);
							if (image.length() > 0) {
								event.setImage(EntityUtil.getImage(url + image, EntityUtil.IMAGE_SIZE, 300));
								if (event.getImage() != null)
									event.setImageList(
											EntityUtil.getImage(url + image, EntityUtil.IMAGE_THUMB_SIZE, 0));
							}
							location.setUrl(url + getField(regexAddressRef, page, 2));
							location.setName(getField(regexName, page, 2));
						}
						params.setQuery(Query.location_listId);
						params.setSearch(
								"location.name like '" + location.getName().replace("'", "_") + "'");
						final Result list = repository.list(params);
						if (list.size() > 0)
							event.setLocationId((BigInteger) list.get(0).get("location.id"));
						else {
							if (!externalPage && !Strings.isEmpty(location.getUrl())) {
								page = eventService.get(location.getUrl());
								location.setAddress(String.join("\n",
										Arrays.asList(getField(regexAddress, page, 2)
												.split(",")).stream().map(e -> e.trim()).toList()));
								location.setDescription(getField(regexDesc, page, 1));
							}
							if (location.getAddress() != null) {
								final String image = getField(regexImage, page, 2);
								if (image.length() > 0) {
									location.setImage(EntityUtil.getImage(url + image, EntityUtil.IMAGE_SIZE, 300));
									if (location.getImage() != null)
										location.setImageList(
												EntityUtil.getImage(url + image, EntityUtil.IMAGE_THUMB_SIZE, 0));
								}
								location.setContactId(client.getAdminId());
								try {
									repository.save(location);
									event.setLocationId(location.getId());
								} catch (IllegalArgumentException ex) {
									if (ex.getMessage().contains("location exists"))
										event.setLocationId(new BigInteger(
												ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim()));
									else
										throw ex;
								}
							}
						}
						if (event.getLocationId() != null) {
							repository.save(event);
							count++;
						}
					}
				}
			}
			return count;
		}

		private String getField(Pattern pattern, String text, int group) {
			final Matcher m = pattern.matcher(text);
			return m.find() ? m.group(group).trim() : "";
		}
	}
}