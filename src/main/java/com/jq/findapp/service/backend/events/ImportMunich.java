package com.jq.findapp.service.backend.events;

import java.io.StringReader;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	private final Pattern regexNextPage = Pattern.compile("<li(.*?)m-pagination__item--next-page(.*?)href=\"(.*?)\"");
	private Client client;
	private EventService eventService;
	private final Set<String> failed = new HashSet<>();

	public int run(final EventService eventService, final BigInteger clientId) throws Exception {
		if (lastRun.get() > System.currentTimeMillis() - 24 * 60 * 60 * 1000)
			return -1;
		final String path = "/veranstaltungen/event";
		lastRun.set(System.currentTimeMillis() + (long) (Math.random() * 5 * 60 * 60 * 1000));
		this.eventService = eventService;
		this.client = repository.one(Client.class, clientId);
		String page = eventService.get(url + path);
		int count = page(page);
		while (true) {
			final Matcher m = regexNextPage.matcher(page);
			if (m.find()) {
				page = eventService.get(url + path + m.group(3).replace("&amp;", "&"));
				count += page(page);
			} else
				break;
		}
		if (failed.size() > 0)
			notificationService.createTicket(TicketType.ERROR, "ImportEventMunich",
					failed.size() + " error:\n" + failed.stream().sorted().collect(Collectors.joining("\n")),
					clientId);
		return count;
	}

	private int page(String page) throws Exception {
		if (!page.contains("<ul class=\"m-listing__list\""))
			return 0;
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
						event.setDescription(
								Strings.sanitize(body.getFirstChild().getFirstChild().getTextContent().trim()
										+ "\n" + getField(externalPage ? regexDescExternal : regexDesc, page, 1),
										1000));
						event.setEndDate(new java.sql.Date(date.getTime()));
						event.setContactId(client.getAdminId());
						event.setSkills(body.getChildNodes().item(1).getTextContent().trim());
						repository.save(event);
						return true;
					}
				} else
					failed.add("location: " + node.getTextContent());
			} catch (final RuntimeException ex) {
				// if unable to access event, then ignore and continue, otherwise re-throw
				if (!ex.getMessage().contains(event.getUrl()) || ex instanceof IllegalArgumentException)
					failed.add(ex.getClass().getName() + ": " + ex.getMessage().replace('\n', ' '));
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
			final String[] s = getField(regexAddressExternal, page, 2).split(",");
			location.setAddress(
					(s.length > 1 ? s[s.length - 2].trim() + "\n" : "") + s[s.length - 1].trim().replace('+', ' '));
			location.setName(s[0].trim() + (s.length > 3 ? ", " + s[1].trim() : ""));
		} else {
			location.setName(getField(regexName, page, 2));
			location.setUrl(getField(regexAddressRef, page, 2));
			if (!Strings.isEmpty(location.getUrl())) {
				location.setUrl(url + location.getUrl());
				page = eventService.get(location.getUrl());
				location.setAddress(String.join("\n",
						Arrays.asList(getField(regexAddress, page, 2)
								.split(",")).stream().map(e -> e.trim()).toList()));
				location.setDescription(Strings.sanitize(getField(regexDesc, page, 1), 1000));
			}
		}
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch(
				"location.name like '" + location.getName().replace("'", "_")
						+ "' and location.country='DE'");
		final Result list = repository.list(params);
		if (list.size() > 0)
			return (BigInteger) list.get(0).get("location.id");
		if (!Strings.isEmpty(location.getAddress())) {
			location.setContactId(client.getAdminId());
			try {
				repository.save(location);
				String image = getField(externalPage ? regexImageExternal : regexImage, page, 2);
				if (image.length() > 0) {
					if (!image.startsWith("http"))
						image = (externalPage ? externalUrl.substring(0, externalUrl.indexOf("/", 10)) : url) + image;
					location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 250));
					if (location.getImage() != null)
						location.setImageList(
								EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
				}
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
