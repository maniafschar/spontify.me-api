package com.jq.findapp.service.events;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
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

	private final Pattern regexAddress = Pattern.compile("itemprop=\"address\"(.*?)</svg>(.*?)</a>");
	private final Pattern regexAddressRef = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
	private final Pattern regexTitle = Pattern.compile("<h3(.*?)>(.*?)<span>(.*?)</span>");
	private final Pattern regexDesc = Pattern.compile(" itemprop=\"description\">(.*?)</(p|div)>");
	private final Pattern regexImage = Pattern.compile("<picture(.*?)<source srcset=\"(.*?) ");

	private final Pattern regexAddressExternal = Pattern.compile("class=\"anfahrt\"(.*?)data-venue=\"(.*?)\"");
	private final Pattern regexAddressRefExternal = Pattern.compile("itemprop=\"location\"(.*?)href=\"(.*?)\"");
	private final Pattern regexImageExternal = Pattern.compile("class=\"show_detail_images\"(.*?) src=\"(.*?)\"");

	private final Pattern regexName = Pattern.compile("itemprop=\"location\"(.*?)</svg>(.*?)</");
	private final Pattern regexPrice = Pattern.compile("Tickets sichern ab &euro; (.*?)</");
	private final Pattern regexNextPage = Pattern.compile("<li(.*?)m-pagination__item--next-page(.*?)href=\"(.*?)\"");
	private final Pattern regexLink = Pattern.compile("<a (.*?)href=\"(.*?)\"");
	private final Pattern regexDatetime = Pattern.compile("<time (.*?)datetime=\"(.*?)\"");

	private Client client;
	private EventService eventService;
	private final Set<String> failed = new HashSet<>();

	public int run(final EventService eventService, final BigInteger clientId) throws Exception {
		final String path = "/veranstaltungen/event";
		this.eventService = eventService;
		this.client = repository.one(Client.class, clientId);
		String page = eventService.get(url + path);
		int count = page(page);
		while (true) {
			final Matcher m = regexNextPage.matcher(page);
			if (m.find()) {
				page = eventService.get(url + path + m.group(3).replace("&amp;", "&"));
				try {
					count += page(page);
				} catch (SAXParseException ex) {
					notificationService.createTicket(TicketType.ERROR, "import munich events",
							page + "\n\n#################\n\n" + Strings.stackTraceToString(ex), clientId);
				}
			} else
				break;
		}
		if (failed.size() > 0)
			notificationService.createTicket(TicketType.ERROR, "ImportEventMunich",
					failed.size() + " errors:\n" + failed.stream().sorted().collect(Collectors.joining("\n")),
					clientId);
		return count;
	}

	private int page(String page) throws Exception {
		int count = 0;
		final String tag = "<li class=\"m-listing__list-item\">";
		if (page.contains(tag))
			page = page.substring(page.indexOf(tag));
		while (page.contains(tag)) {
			String li = page.substring(0, page.indexOf("</li>"));
			try {
				if (importNode(li))
					count++;
			} catch (final Exception ex) {
				notificationService.createTicket(TicketType.ERROR, "eventImport",
						Strings.stackTraceToString(ex) + "\n\n" + li, null);
			}
			page = page.substring(page.indexOf("</li>") + 5);
		}
		return count;
	}

	private boolean importNode(final String li) throws Exception {
		final Event event = new Event();
		Matcher m = regexLink.matcher(li);
		if (m.find()) {
			final String link = m.group(2);
			event.setUrl((link.startsWith("https://") ? "" : url) + link);
		} else
			return false;
		final boolean externalPage = event.getUrl().startsWith("https://www.muenchenticket.de/");
		if (event.getUrl().startsWith(url) || externalPage) {
			try {
				final String page = eventService.get(event.getUrl());
				event.setLocationId(importLocation(page, externalPage, event.getUrl()));
				m = regexDatetime.matcher(li);
				for (int i = 0; i < 3; i++)
					m.find();
				final Date date = new SimpleDateFormat("dd.MM.yyyy' - 'HH:mm").parse(m.group(2));
				event.setStartDate(new Timestamp(date.getTime()));
				event.setEndDate(new java.sql.Date(date.getTime()));
				final QueryParams params = new QueryParams(Query.event_listId);
				params.setSearch("event.startDate=cast('"
						+ event.getStartDate().toInstant().toString().substring(0, 19)
						+ "' as timestamp) and event.locationId=" + event.getLocationId() + " and event.contactId="
						+ client.getAdminId());
				if (repository.list(params).size() == 0) {
					if (externalPage) {
						m = regexPrice.matcher(page);
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
					event.setDescription(getField(regexDesc, page, 1));
					m = regexTitle.matcher(li);
					if (m.find())
						event.setDescription(m.group(3) + "\n" + event.getDescription());
					event.setDescription(Strings.sanitize(event.getDescription(), 1000));
					event.setContactId(client.getAdminId());
					event.setType(EventType.Location);
					repository.save(event);
					return true;
				}
			} catch (final RuntimeException ex) {
				// if unable to access event, then ignore and continue, otherwise re-throw
				if (ex instanceof IllegalArgumentException)
					failed.add(ex.getMessage().replace("\n", "\n  "));
				else if (!ex.getMessage().contains(event.getUrl()))
					failed.add(ex.getClass().getName() + ": " + ex.getMessage().replace("\n", "\n  "));
			}
		} else
			failed.add("event: " + event.getUrl());
		return false;
	}

	private BigInteger importLocation(String page, final boolean externalPage, final String externalUrl)
			throws Exception {
		final Location location = new Location();
		if (externalPage) {
			location.setUrl(getField(regexAddressRefExternal, page, 2));
			if (!location.getUrl().startsWith("https://"))
				location.setUrl(externalUrl + location.getUrl());
			final String[] s = getField(regexAddressExternal, page, 2).split(",");
			location.setAddress(
					(s.length > 1 ? s[s.length - 2].trim() + "\n" : "") + s[s.length - 1].trim().replace('+', ' '));
			location.setName(s[0].trim() + (s.length > 3 ? ", " + s[1].trim() : ""));
		} else {
			location.setName(getField(regexName, page, 2));
			location.setUrl(getField(regexAddressRef, page, 2));
			if (!Strings.isEmpty(location.getUrl())) {
				if (!location.getUrl().startsWith("https://"))
					location.setUrl(url + location.getUrl());
				page = eventService.get(location.getUrl());
				location.setAddress(String.join("\n",
						Arrays.asList(getField(regexAddress, page, 2)
								.split(",")).stream().map(e -> e.trim()).toList()));
				location.setDescription(Strings.sanitize(getField(regexDesc, page, 1), 1000));
			}
		}
		if (!Strings.isEmpty(location.getAddress())) {
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("location.name like '" + location.getName().replace("'", "_")
					+ "' and '" + location.getAddress().toLowerCase().replace('\n', ' ')
					+ "' like concat('%',LOWER(location.zipCode),'%')");
			final Result list = repository.list(params);
			if (list.size() > 0)
				return (BigInteger) list.get(0).get("location.id");
			location.setContactId(client.getAdminId());
			try {
				repository.save(location);
				String image = getField(externalPage ? regexImageExternal : regexImage, page, 2);
				if (image.length() > 0) {
					if (!image.startsWith("http"))
						image = (externalPage ? externalUrl.substring(0, externalUrl.indexOf("/", 10)) : url) + image;
					location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 250));
					if (location.getImage() != null) {
						location.setImageList(
								EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
						repository.save(location);
					}
				}
				return location.getId();
			} catch (final IllegalArgumentException ex) {
				if (ex.getMessage().contains("location exists"))
					return new BigInteger(
							ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim());
				else
					throw ex;
			}
		}
		throw new RuntimeException(
				"Name: " + location.getName() + " | URL: " + location.getUrl() + " | Address: " + location.getAddress()
						+ " | Page: " + page);
	}

	private String getField(final Pattern pattern, final String text, final int group) {
		final Matcher m = pattern.matcher(text);
		return m.find() ? m.group(group).trim() : "";
	}
}
