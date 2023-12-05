package com.jq.findapp.service.backend.events;

import java.io.StringReader;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;

@Component
public class ImportMunich {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

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

	public int run(final EventService eventService) throws Exception {
		if (lastRun.get() > System.currentTimeMillis() - 24 * 60 * 60 * 1000)
			return 0;
		lastRun.set(System.currentTimeMillis() + (long) (Math.random() * 5 * 60 * 60 * 1000));
		this.eventService = eventService;
		client = repository.one(Client.class, BigInteger.ONE);
		final int count = page(eventService.get(url + "/veranstaltungen/event"));
		// page 2: count += page(urlRetriever.get(url + "/veranstaltungen/event"));
		return count;
	}

	public int publish(final EventService eventService) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listId);
		params.setSearch("event.startDate>'" + Instant.now().plus(Duration.ofHours(6))
				+ "' and event.startDate<'" + Instant.now().plus(Duration.ofHours(30))
				+ "' and event.contactId=" + client.getAdminId()
				+ " and (event.image is not null or location.image is not null)"
				+ " and event.url is not null"
				+ " and event.repetition='o'"
				+ " and event.maxParticipants is null"
				+ " and location.zipCode like '8%'"
				+ " and location.country='DE'");
		final Result result = repository.list(params);
		for (int i = 0; i < result.size(); i++) {
			if (result.get(i).get("event.modifiedAt") != null &&
					((Timestamp) result.get(i).get("event.modifiedAt")).getTime() > Instant.now().minus(Duration.ofMinutes(35)).toEpochMilli());
				return -result.size();
		}
		for (int i = 0; i < result.size() && i < 2; i++) {
			if (Strings.isEmpty(result.get(i).get("event.publishId")))
				eventService.publish((BigInteger) result.get(i).get("event.id"));
		}
		return count;
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
				try {
					if (importNode(lis.item(i).getFirstChild()))
						count++;
				} catch (final Exception ex) {
					notificationService.createTicket(TicketType.ERROR, "eventImport",
							Strings.stackTraceToString(ex) + "\n\n" + lis.item(i).getTextContent(),
							null);
				}
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
		if (!Strings.isEmpty(event.getUrl()) &&
				(event.getUrl().startsWith(url) || event.getUrl().startsWith("https://www.muenchenticket.de/"))) {
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
								+ "\n" + getField(externalPage ? regexDescExternal : regexDesc, page, 1))
								.replaceAll("<[^>]*>", " ").trim());
						event.setEndDate(new java.sql.Date(date.getTime()));
						event.setContactId(client.getAdminId());
						event.setSkills(body.getChildNodes().item(1).getTextContent().trim());
						if (event.getDescription().length() > 1000)
							event.setDescription(event.getDescription().substring(0, 997) + "...");
						repository.save(event);
						return true;
					}
				} else
					throw new IllegalArgumentException("unable to import location: " + node.getTextContent());
			} catch (final RuntimeException ex) {
				// if unable to access event, then ignore and continue, otherwise re-throw
				if (!ex.getMessage().contains(event.getUrl()) || ex instanceof IllegalArgumentException)
					throw ex;
			}
		} else
			throw new IllegalArgumentException("unknown details URL: " + event.getUrl());
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
			location.setDescription(getField(regexDesc, page, 1).replaceAll("<[^>]*>", " ").trim());
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
