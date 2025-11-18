package com.jq.findapp.service.event;

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
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Entity;
import com.jq.findapp.util.Strings;

@Component
abstract class Import {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	protected BigInteger clientId;
	protected String url;
	protected String urlExternal;
	protected String path;

	protected Pattern regexListStartTag;
	protected Pattern regexListEndTag;
	protected Pattern regexLink;
	protected Pattern regexNextPage;

	protected Pattern regexTitle;
	protected Pattern regexDesc;
	protected Pattern regexPrice;
	protected Pattern regexImage;
	protected Pattern regexDatetime;

	protected Pattern regexName;
	protected Pattern regexAddress;
	protected Pattern regexAddressRef;

	protected Pattern regexAddressExternal;
	protected Pattern regexAddressRefExternal;
	protected Pattern regexImageExternal;

	private Client client;
	private final Set<String> failed = new HashSet<>();

	public int run() throws Exception {
		this.client = this.repository.one(Client.class, this.clientId);
		String page = this.get(this.url + this.path);
		int count = this.page(page);
		while (true) {
			final Matcher m = this.regexNextPage.matcher(page);
			if (m.find()) {
				page = this.get(this.url + this.path + m.group(3).replace("&amp;", "&"));
				count += this.page(page);
			} else
				break;
		}
		if (this.failed.size() > 0)
			this.notificationService.createTicket(TicketType.ERROR, "ImportEventMunich",
					this.failed.size() + " errors:\n" + this.failed.stream().sorted().collect(Collectors.joining("\n")),
					this.clientId);
		return count;
	}

	protected String get(final String url) {
		return Strings.urlContent(url)
				.replace('\n', ' ')
				.replace('\r', ' ')
				.replace('\u0013', ' ')
				.replace('\u001c', ' ')
				.replace('\u001e', ' ');
	}

	private int page(String page) throws Exception {
		int count = 0;
		Matcher matcher = this.regexListStartTag.matcher(page);
		while (matcher.find()) {
			page = page.substring(matcher.start());
			matcher = this.regexListEndTag.matcher(page);
			if (matcher.find()) {
				final String li = page.substring(0, matcher.start());
				try {
					if (this.importNode(li))
						count++;
				} catch (final Exception ex) {
					this.notificationService.createTicket(TicketType.ERROR, "eventImport",
							Strings.stackTraceToString(ex) + "\n\n" + li, null);
				}
				page = page.substring(matcher.start() + this.regexListEndTag.pattern().length());
				matcher = this.regexListStartTag.matcher(page);
			}
		}
		return count;
	}

	private boolean importNode(final String li) throws Exception {
		final Event event = new Event();
		Matcher m = this.regexLink.matcher(li);
		if (m.find()) {
			final String link = m.group(2);
			event.setUrl((link.startsWith("https://") ? "" : this.url) + link);
		} else
			return false;
		final boolean externalPage = this.urlExternal != null && event.getUrl().startsWith(this.urlExternal);
		if (event.getUrl().startsWith(this.url) || externalPage) {
			try {
				final String page = this.get(event.getUrl());
				event.setLocationId(this.importLocation(page, externalPage, event.getUrl()));
				m = this.regexDatetime.matcher(li);
				for (int i = 0; i < 3; i++)
					m.find();
				final Date date = new SimpleDateFormat("dd.MM.yyyy' - 'HH:mm").parse(m.group(2));
				event.setStartDate(new Timestamp(date.getTime()));
				event.setEndDate(new java.sql.Date(date.getTime()));
				final QueryParams params = new QueryParams(Query.event_listId);
				params.setSearch("event.startDate=cast('"
						+ event.getStartDate().toInstant().toString().substring(0, 19)
						+ "' as timestamp) and event.locationId=" + event.getLocationId() + " and event.contactId="
						+ this.client.getAdminId());
				if (this.repository.list(params).size() == 0) {
					if (externalPage) {
						m = this.regexPrice.matcher(page);
						if (m.find())
							event.setPrice(Double.valueOf(m.group(1).replace(".", "").replace(",", "")) / 100);
					} else {
						final String image = this.getField(this.regexImage, page, 2);
						if (image.length() > 0)
							Entity.addImage(event, this.url + image);
					}
					event.setDescription(this.getField(this.regexDesc, page, 1));
					m = this.regexTitle.matcher(li);
					if (m.find())
						event.setDescription(m.group(3) + "\n" + event.getDescription());
					event.setDescription(Strings.sanitize(event.getDescription(), 1000));
					event.setContactId(this.client.getAdminId());
					event.setType(EventType.Location);
					this.repository.save(event);
					return true;
				}
			} catch (final RuntimeException ex) {
				// if unable to access event, then ignore and continue, otherwise re-throw
				if (ex instanceof IllegalArgumentException)
					this.failed.add(ex.getMessage().replace("\n", "\n  "));
				else if (!ex.getMessage().contains(event.getUrl()))
					this.failed.add(ex.getClass().getName() + ": " + ex.getMessage().replace("\n", "\n  "));
			}
		} else
			this.failed.add("event: " + event.getUrl());
		return false;
	}

	private BigInteger importLocation(String page, final boolean externalPage, final String externalUrl)
			throws Exception {
		final Location location = new Location();
		if (externalPage) {
			location.setUrl(this.getField(this.regexAddressRefExternal, page, 2));
			if (!location.getUrl().startsWith("https://"))
				location.setUrl(externalUrl + location.getUrl());
			final String[] s = this.getField(this.regexAddressExternal, page, 2).split(",");
			location.setAddress(
					(s.length > 1 ? s[s.length - 2].trim() + "\n" : "") + s[s.length - 1].trim().replace('+', ' '));
			location.setName(s[0].trim() + (s.length > 3 ? ", " + s[1].trim() : ""));
		} else {
			location.setName(this.getField(this.regexName, page, 2));
			location.setUrl(this.getField(this.regexAddressRef, page, 2));
			if (!Strings.isEmpty(location.getUrl())) {
				if (!location.getUrl().startsWith("https://"))
					location.setUrl(this.url + location.getUrl());
				page = this.get(location.getUrl());
				location.setAddress(String.join("\n",
						Arrays.asList(this.getField(this.regexAddress, page, 2)
								.split(",")).stream().map(e -> e.trim()).toList()));
				location.setDescription(Strings.sanitize(this.getField(this.regexDesc, page, 1), 1000));
			}
		}
		location.setContactId(this.client.getAdminId());
		String image = this.getField(externalPage ? this.regexImageExternal : this.regexImage, page, 2);
		if (image.length() > 0 && !image.startsWith("http"))
			image = (externalPage ? externalUrl.substring(0, externalUrl.indexOf("/", 10)) : this.url) + image;
		final BigInteger id = this.importLocation(location, image);
		if (id == null)
			throw new RuntimeException(
					"Name: " + location.getName() + " | URL: " + location.getUrl() + " | Address: "
							+ location.getAddress()
							+ " | Page: " + externalUrl);
		return id;
	}

	private String getField(final Pattern pattern, final String text, final int group) {
		final Matcher m = pattern.matcher(text);
		return m.find() ? m.group(group).trim() : "";
	}

	public BigInteger importLocation(Location location, final String image) throws Exception {
		if (!Strings.isEmpty(location.getAddress())) {
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("location.name like '" + location.getName().replace("'", "_")
					+ "' and '" + location.getAddress().toLowerCase().replace('\n', ' ')
					+ "' like concat('%',LOWER(location.zipCode),'%')");
			final Result list = this.repository.list(params);
			if (list.size() > 0)
				location = this.repository.one(Location.class, (BigInteger) list.get(0).get("location.id"));
			else {
				try {
					this.repository.save(location);
				} catch (final IllegalArgumentException ex) {
					if (ex.getMessage().startsWith("exists:"))
						location = this.repository.one(Location.class, new BigInteger(
								ex.getMessage().substring(ex.getMessage().indexOf(':') + 1).trim()));
					else
						throw ex;
				}
			}
			if (!Strings.isEmpty(image) && Strings.isEmpty(location.getImage())) {
				try {
					Entity.addImage(location, image);
					this.repository.save(location);
				} catch (final IllegalArgumentException ex) {
					// save location wihtout image
				}
			}
		}
		return location.getId();
	}
}
