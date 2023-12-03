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
import java.util.concurrent.atomic.AtomicLong;
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
		externalService.publishOnFacebook(contact.getClientId(),
				new SimpleDateFormat("d.M.yyyy H:mm").format(event.getStartDate()) + "\n" + location.getName() + "\n"
						+ location.getAddress() + "\n\n"
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
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	protected String get(final String url) {
		try {
			return IOUtils.toString(new URL(url).openStream(), StandardCharsets.UTF_8).replace('\n', ' ').replace('\r',
					' ');
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Component
	private static class ImportMunich {
		@Autowired
		private Repository repository;

		@Value("${app.event.munich.baseUrl}")
		private String url;

		private static final AtomicLong lastRun = new AtomicLong(0);

		private final Pattern regexAddress = Pattern.compile("itemprop=\"address\"(.*?)</svg>(.*?)</a>");
		private final Pattern regexAddressRef = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		private final Pattern regexDesc = Pattern.compile(" itemprop=\"description\">(.*?)</p>");
		private final Pattern regexImage = Pattern.compile("<picture(.*?)<source srcset=\"(.*?) ");

		private final Pattern regexAddressExternal = Pattern.compile("class=\"anfahrt\"(.*?)data-venue=\"(.*?)\"");
		private final Pattern regexAddressRefExternal = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
		private final Pattern regexDescExternal = Pattern.compile(" itemprop=\"description\">(.*?)</div>");
		private final Pattern regexImageExternal = Pattern.compile("class=\"show_detail_images\"(.*?) src=\"(.*?)\"");

		private final Pattern regexName = Pattern.compile("itemprop=\"location\"(.*?)</svg>(.*?)</");
		private final Pattern regexPrice = Pattern.compile("Tickets sichern ab &euro; (.*?)</");
		private Client client;
		private EventService eventService;

		private String run(final EventService eventService) throws Exception {
			if (lastRun.get() > System.currentTimeMillis() - 24 * 60 * 60 * 1000)
				return "";
			lastRun.set(System.currentTimeMillis() + (long) (Math.random() * 5 * 60 * 60 * 1000));
			this.eventService = eventService;
			client = repository.one(Client.class, BigInteger.ONE);
			final int count = page(eventService.get(url + "/veranstaltungen/event"));
			// page 2: count += page(urlRetriever.get(url + "/veranstaltungen/event"));
			final QueryParams params = new QueryParams(Query.event_listId);
			params.setSearch(
					"event.startDate>'" + Instant.now().plus(Duration.ofHours(6)) + "' and event.startDate<'"
							+ Instant.now().plus(Duration.ofHours(30))
							+ "' and event.contactId=" + client.getAdminId()
							+ " and event.url is not null and event.repetition='o' and event.maxParticipants is null and location.zipCode like '8%' and location.country='DE'");
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++)
				eventService.publish((BigInteger) result.get(i).get("event.id"));
			return "Munich: " + count + " imported, " + result.size() + " published";
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
					if (importNode(lis.item(i).getFirstChild()))
						count++;
				}
			}
			return count;
		}

		private boolean importNode(final Node node) throws Exception {
			final Node body = node.getChildNodes().item(1);
			final Event event = new Event();
			final boolean externalPage = node.getChildNodes().item(2) != null;
			if (externalPage)
				event.setUrl(
						node.getChildNodes().item(2).getFirstChild().getNextSibling()
								.getAttributes().getNamedItem("href").getNodeValue().trim());
			else if (body.getFirstChild().getFirstChild().getAttributes() != null)
				event.setUrl(url + body.getFirstChild().getFirstChild()
						.getAttributes().getNamedItem("href").getNodeValue().trim());
			if (!Strings.isEmpty(event.getUrl()) && !event.getUrl().contains("eventim")) {
				try {
					final String page = eventService.get(event.getUrl());
					event.setLocationId(importLocation(page, externalPage, event.getUrl()));
					if (event.getLocationId() != null) {
						final Date date = new SimpleDateFormat("dd.MM.yyyy' - 'HH:mm")
								.parse(body.getChildNodes().item(2).getChildNodes().item(3)
										.getAttributes().getNamedItem("datetime").getNodeValue());
						event.setStartDate(new Timestamp(date.getTime()));
						final QueryParams params = new QueryParams(Query.event_listId);
						params.setSearch("event.startDate='"
								+ event.getStartDate().toInstant().toString().substring(0, 19)
								+ "' and event.locationId=" + event.getLocationId() + " and event.contactId="
								+ client.getAdminId());
						if (repository.list(params).size() == 0) {
							if (externalPage) {
								final Matcher m = regexPrice.matcher(page);
								if (m.find())
									event.setPrice(Double.valueOf(m.group(1).replace(".", "").replace(",", "")) / 100);
							} else {
								final String image = getField(regexImage, page, 2);
								if (image.length() > 0) {
									event.setImage(EntityUtil.getImage(url + image, EntityUtil.IMAGE_SIZE, 300));
									if (event.getImage() != null)
										event.setImageList(
												EntityUtil.getImage(url + image, EntityUtil.IMAGE_THUMB_SIZE, 0));
								}
							}
							event.setDescription((body.getFirstChild().getFirstChild().getTextContent().trim()
									+ "\n" + getField(externalPage ? regexDescExternal : regexDesc, page, 1)).trim());
							event.setEndDate(new java.sql.Date(date.getTime()));
							event.setContactId(client.getAdminId());
							event.setSkillsText(body.getChildNodes().item(1).getTextContent().trim());
							if (event.getDescription().length() > 1000)
								event.setDescription(event.getDescription().substring(0, 997) + "...");
							repository.save(event);
							return true;
						}
					}
				} catch (final RuntimeException ex) {
					// if unable to access event, then ignore and continue, otherwise re-throw
					if (!ex.getMessage().contains(event.getUrl()))
						throw ex;
				}
			}
			return false;
		}

		private BigInteger importLocation(String page, final boolean externalPage, final String externalUrl)
				throws Exception {
			final Location location = new Location();
			if (externalPage) {
				location.setUrl(getField(regexAddressRefExternal, page, 2));
				final String[] s = getField(regexAddressExternal, page, 2).split(",");
				location.setAddress(
						(s.length > 1 ? s[s.length - 2].trim() + "\n" : "") + s[s.length - 1].trim().replace('+', ' '));
				location.setName(s[0].trim() + (s.length > 3 ? ", " + s[1].trim() : ""));
			} else {
				location.setUrl(url + getField(regexAddressRef, page, 2));
				location.setName(getField(regexName, page, 2));
				page = eventService.get(location.getUrl());
				location.setAddress(String.join("\n",
						Arrays.asList(getField(regexAddress, page, 2)
								.split(",")).stream().map(e -> e.trim()).toList()));
				location.setDescription(getField(regexDesc, page, 1));
			}
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch(
					"location.name like '" + location.getName().replace("'", "_")
							+ "' and location.country='DE'");
			final Result list = repository.list(params);
			if (list.size() > 0)
				return (BigInteger) list.get(0).get("location.id");
			if (!Strings.isEmpty(location.getAddress())) {
				String image = getField(externalPage ? regexImageExternal : regexImage, page, 2);
				if (image.length() > 0) {
					if (!image.startsWith("http"))
						image = (externalPage ? externalUrl.substring(0, externalUrl.indexOf("/", 10)) : url) + image;
					location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 250));
					if (location.getImage() != null)
						location.setImageList(
								EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
				}
				location.setContactId(client.getAdminId());
				if (location.getDescription() != null && location.getDescription().length() > 1000)
					location.setDescription(location.getDescription().substring(0, 997) + "...");
				try {
					repository.save(location);
					return location.getId();
				} catch (final IllegalArgumentException ex) {
					if (ex.getMessage().contains("location exists"))
						return new BigInteger(
								ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim());
					else
						throw ex;
				}
			}
			return null;
		}

		private String getField(final Pattern pattern, final String text, final int group) {
			final Matcher m = pattern.matcher(text);
			return m.find() ? m.group(group).trim() : "";
		}
	}
}