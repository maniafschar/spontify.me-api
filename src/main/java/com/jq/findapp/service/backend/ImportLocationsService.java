package com.jq.findapp.service.backend;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class ImportLocationsService {
	private final Pattern href = Pattern.compile("href=\"([^\"]*)\"");

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	private static final LocationType[] TYPES = {
			new LocationType("accounting"),
			new LocationType("airport", 3, 13),
			new LocationType("amusement_park", 3, 14),
			new LocationType("aquarium", 3, 15),
			new LocationType("art_gallery", 3, 1),
			new LocationType("atm"),
			new LocationType("bakery", 0, 22),
			new LocationType("bank", 0, 23),
			new LocationType("bar", 4, 30),
			new LocationType("beauty_salon", 0, 24),
			new LocationType("bicycle_store", 0, 25),
			new LocationType("book_store", 0, 26),
			new LocationType("bowling_alley", 5, 24),
			new LocationType("bus_station"),
			new LocationType("cafe", 2, 24),
			new LocationType("campground", 3, 16),
			new LocationType("car_dealer", 0, 6),
			new LocationType("car_rental"),
			new LocationType("car_repair"),
			new LocationType("car_wash"),
			new LocationType("casino", 4, 31),
			new LocationType("cemetery", 3, 17),
			new LocationType("church", 1, 24),
			new LocationType("city_hall", 3, 18),
			new LocationType("clothing_store", 0, 1),
			new LocationType("convenience_store", 0, 30),
			new LocationType("courthouse", 3, 19),
			new LocationType("dentist", 0, 28),
			new LocationType("department_store", 0, 27),
			new LocationType("doctor", 0, 28),
			new LocationType("drugstore", 0, 20),
			new LocationType("electrician"),
			new LocationType("electronics_store"),
			new LocationType("embassy", 3, 20),
			new LocationType("establishment"),
			new LocationType("fire_station", 3, 21),
			new LocationType("florist", 0, 11),
			new LocationType("funeral_home"),
			new LocationType("furniture_store", 0, 16),
			new LocationType("gas_station", 0, 29),
			new LocationType("grocery_or_supermarket", 0, 30),
			new LocationType("gym", 5, 27),
			new LocationType("hair_care", 0, 21),
			new LocationType("hardware_store", 0, 31),
			new LocationType("health", 0, 20),
			new LocationType("hindu_temple", 1, 24),
			new LocationType("home_goods_store", 0, 32),
			new LocationType("hospital"),
			new LocationType("insurance_agency"),
			new LocationType("jewelry_store", 0, 33),
			new LocationType("laundry"),
			new LocationType("lawyer"),
			new LocationType("library", 1, 25),
			new LocationType("light_rail_station"),
			new LocationType("liquor_store", 0, 34),
			new LocationType("local_government_office"),
			new LocationType("locksmith"),
			new LocationType("lodging", 3, 11),
			new LocationType("meal_delivery", 2, null),
			new LocationType("meal_takeaway", 2, null),
			new LocationType("mosque", 1, 24),
			new LocationType("movie_rental"),
			new LocationType("movie_theater", 1, 8),
			new LocationType("moving_company"),
			new LocationType("museum", 3, 1),
			new LocationType("night_club", 5, 21),
			new LocationType("painter"),
			new LocationType("park", 3, 14),
			new LocationType("parking"),
			new LocationType("pet_store", 0, 37),
			new LocationType("pharmacy", 0, 20),
			new LocationType("physiotherapist", 0, 20),
			new LocationType("plumber"),
			new LocationType("point_of_interest"),
			new LocationType("police"),
			new LocationType("post_office", 0, 36),
			new LocationType("primary_school"),
			new LocationType("real_estate_agency"),
			new LocationType("restaurant", 2, null),
			new LocationType("roofing_contractor"),
			new LocationType("rv_park"),
			new LocationType("school"),
			new LocationType("secondary_school"),
			new LocationType("shoe_store", 0, 0),
			new LocationType("shopping_mall", 0, 27),
			new LocationType("spa", 3, 35),
			new LocationType("stadium", 3, 22),
			new LocationType("storage"),
			new LocationType("store", 0, 27),
			new LocationType("subway_station"),
			new LocationType("supermarket", 0, 30),
			new LocationType("synagogue", 1, 24),
			new LocationType("taxi_stand"),
			new LocationType("tourist_attraction", 3, 0),
			new LocationType("train_station"),
			new LocationType("transit_station"),
			new LocationType("travel_agency"),
			new LocationType("university", 1, 26),
			new LocationType("veterinary_care", 0, 20),
			new LocationType("zoo", 3, 7)
	};

	private static class LocationType {
		private final String google;
		private final Integer category;
		private final Integer subCategory;

		private LocationType(final String google) {
			this(google, null, null);
		}

		private LocationType(final String google, final Integer category, final Integer subCategory) {
			this.google = google;
			this.category = category;
			this.subCategory = subCategory;
		}
	}

	@Async
	public void lookup(final float latitude, final float longitude) {
		final float roundingFactor = 1000f;
		final float lat = ((int) (latitude * roundingFactor) / roundingFactor);
		final float lon = ((int) (longitude * roundingFactor) / roundingFactor);
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		final String s = lat + "\n" + lon;
		params.setSearch("ticket.subject='import' and ticket.note like '" + s +
				"%' and ticket.type='" + TicketType.LOCATION.name() + "'");
		if (repository.list(params).size() == 0) {
			String importResult = null;
			JsonNode json = Json
					.toNode(externalService.google("place/nearbysearch/json?radius=600&sensor=false&location="
							+ latitude + "," + longitude));
			if ("OK".equals(json.get("status").asText())) {
				importResult = importLocations(json.get("results").elements());
				while (json.has("next_page_token")) {
					json = Json.toNode(externalService.google(
							"place/nearbysearch/json?pagetoken=" + json.get("next_page_token").asText()));
					if ("OK".equals(json.get("status").asText()))
						importResult += "\n" + importLocations(json.get("results").elements());
					else
						break;
				}
			}
			notificationService.createTicket(TicketType.LOCATION, "import",
					s + (importResult == null ? "" : "\n" + importResult), null);
		}
	}

	public String importLocation(final BigInteger ticketId, final String category) throws Exception {
		final Ticket ticket = repository.one(Ticket.class, ticketId);
		String result = null;
		try {
			importLocation(Json.toNode(Attachment.resolve(ticket.getNote())), category);
		} catch (final IllegalArgumentException ex) {
			result = ex.getMessage();
		} catch (final Exception ex) {
			result = Strings.stackTraceToString(ex);
		}
		return result;
	}

	private String importLocations(final Iterator<JsonNode> result) {
		int imported = 0, total = 0;
		String town = null;
		Location location;
		while (result.hasNext()) {
			final JsonNode json = result.next();
			total++;
			location = json2location(json);
			if (json.has("photos") && mapFirstRelevantType(json) != null &&
					(!json.has("permanently_closed") || !json.get("permanently_closed").asBoolean()) &&
					json.has("rating") && json.get("rating").asDouble() > 3) {
				try {
					final String jsonLower = Json.toString(json).toLowerCase();
					if (jsonLower.contains("sex") || jsonLower.contains("domina") || jsonLower.contains("bordel")) {
						notificationService.createTicket(TicketType.LOCATION,
								location.getCategory() + " " + location.getName(),
								Json.toPrettyString(json), null);
					} else {
						try {
							if ((location = importLocation(json, null)) != null) {
								imported++;
								if (town == null)
									town = location.getCountry() + " " + location.getTown() + " "
											+ location.getZipCode();
							}
						} catch (final Exception ex) {
							if (location != null && (ex.getMessage() == null
									|| (!ex.getMessage().contains("NO_IMAGE")
											&& !ex.getMessage().contains("invalid address")
											&& !ex.getMessage().contains("location exists")
											&& !Strings.stackTraceToString(ex).toLowerCase()
													.contains("duplicate entry")))) {
								notificationService.createTicket(TicketType.LOCATION,
										location.getCategory() + " " + location.getName(),
										Json.toPrettyString(json), null);
							}
						}
					}
				} catch (final Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		}
		return imported + "/" + total + (town == null ? "" : "\n" + town);
	}

	private LocationType mapType(final String typeGoogle) {
		for (final LocationType type : TYPES) {
			if (typeGoogle.equals(type.google))
				return type;
		}
		return null;
	}

	private LocationType mapFirstRelevantType(final JsonNode json) {
		final JsonNode types = json.get("types");
		if (types != null && types.size() > 0) {
			final Iterator<JsonNode> type = types.elements();
			while (type.hasNext()) {
				final LocationType t = mapType(type.next().asText());
				if (t != null && t.category != null)
					return t;
			}
		}
		return null;
	}

	public CronResult run() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch("location.image is null");
		params.setLimit(5);
		final Result list = repository.list(params);
		result.body = list.size() + " locations for update\n";
		int updated = 0, exceptions = 0;
		for (int i = 0; i < list.size(); i++) {
			try {
				if (importImage(repository.one(Location.class, (BigInteger) list.get(i).get("location.id"))))
					updated++;
			} catch (Exception ex) {
				exceptions++;
				result.exception = ex;
			}
		}
		result.body += (updated > 0 ? updated + " updated\n" : "") + (exceptions > 0 ? exceptions + " exceptions" : "");
		return result;
	}

	private boolean importImage(final Location location) throws Exception {
		final String address = location.getAddress().replace("\n", ", ");
		final JsonNode json = Json.toNode(
				externalService.google("place/textsearch/json?query="
						+ URLEncoder.encode(location.getName() + ", " + address, StandardCharsets.UTF_8)
								.replace("+", "%20")));
		if ("OK".equals(json.get("status").asText())) {
			final JsonNode results = json.get("results");
			for (int i2 = 0; i2 < results.size(); i2++) {
				if (results.get(i2).has("photos")) {
					final JsonNode photos = results.get(i2).get("photos");
					for (int i3 = 0; i3 < photos.size(); i3++) {
						if (photos.get(i3).has("photo_reference")) {
							final String html = externalService.google(
									"place/photo?maxheight=1200&photoreference="
											+ photos.get(i3).get("photo_reference").asText())
									.replace("<A HREF=", "<a href=");
							final Matcher matcher = href.matcher(html);
							if (matcher.find()) {
								final String image = matcher.group(1);
								try {
									location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 0));
									location.setImageList(
											EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
									repository.save(location);
									return true;
								} catch (IllegalArgumentException ex) {
									if (!ex.getMessage().contains("IMAGE_TOO_SMALL"))
										notificationService.createTicket(TicketType.ERROR, "importImage",
												Strings.stackTraceToString(ex), null);
								}
							}
						}
					}
					if (!Strings.isEmpty(location.getImage()))
						break;
				}
			}
		}
		location.setImage("");
		repository.save(location);
		return false;
	}

	public CronResult runUrl() {
		final CronResult result = new CronResult();
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch(
				"length(location.url)>0 and (location.image is null or location.email is null) and location.skills<>'X'");
		final Result list = repository.list(params);
		result.body = list.size() + " locations for update\n";
		int updated = 0, exceptions = 0;
		for (int i = 0; i < list.size(); i++) {
			try {
				final Location location = repository.one(Location.class,
						(BigInteger) list.get(i).get("location.id"));
				importEmailImage(location);
				repository.save(location);
				updated++;
			} catch (Exception ex) {
				exceptions++;
				result.exception = ex;
			}
		}
		result.body += (updated > 0 ? updated + " updated\n" : "") + (exceptions > 0 ? exceptions + " exceptions" : "");
		return result;
	}

	private void importEmailImage(final Location location) throws Exception {
		String html = IOUtils.toString(new URI(location.getUrl()), StandardCharsets.UTF_8).toLowerCase();
		if (Strings.isEmpty(location.getImage())) {
			findImage(html, "src=\"([^\"]*)\"", location);
			if (Strings.isEmpty(location.getImage()))
				findImage(html, "url([^)]*)", location);
		}
		if (Strings.isEmpty(location.getEmail()) && html.contains("impressum")) {
			html = html.substring(0, html.lastIndexOf("impressum"));
			int i = html.lastIndexOf("href=\"");
			if (i > -1) {
				i += 6;
				html = html.substring(i, html.indexOf('"', i));
				if (!html.startsWith("http"))
					html = location.getUrl() + (location.getUrl().endsWith("/") ? "" : "/") + html;
				html = IOUtils.toString(new URI(html), StandardCharsets.UTF_8)
						.toLowerCase().replace("[at]", "@").replace("(*at*)", "@");
				int pos = html.length();
				final Pattern emailPattern = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*",
						Pattern.CASE_INSENSITIVE);
				while ((pos = html.lastIndexOf('@', pos - 1)) > 0) {
					final Matcher matcher = emailPattern.matcher(
							html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length())));
					if (matcher.find()) {
						final String email = matcher.group(1);
						if (!email.endsWith(".png") && !email.endsWith(".jpg")
								&& (email.endsWith("@web.de") || email.endsWith("@gmx.de")
										|| email.endsWith("@t-online.de")
										|| location.getUrl()
												.contains(email.substring(matcher.group(1).indexOf("@") + 1))
										||
										location.getUrl()
												.contains(email.substring(0, matcher.group(1).indexOf("@"))))) {
							location.setEmail(email);
							break;
						}
					}
				}
			}
		}
	}

	private void findImage(String html, String pattern, Location location) {
		final Matcher matcher = Pattern.compile(pattern).matcher(html);
		final int min = 350;
		int size = 0;
		String urlImage = null;
		while (matcher.find()) {
			String m = matcher.group(1).toLowerCase();
			if (m.endsWith(".jpg") || m.endsWith(".jpeg") || m.endsWith(".png")) {
				try {
					final BufferedImage img = ImageIO
							.read(new ByteArrayInputStream(
									IOUtils.toByteArray(new URI(m.contains("://") ? m : location.getUrl() + "/" + m))));
					if (size < img.getWidth() * img.getHeight() && img.getWidth() > min && img.getHeight() > min) {
						urlImage = m;
						size = img.getWidth() * img.getHeight();
					}
				} catch (Exception ex) {
				}
			}
		}
		if (urlImage != null && size > 150000) {
			location.setImage(EntityUtil.getImage(urlImage, min, 0));
			location.setImageList(EntityUtil.getImage(urlImage, EntityUtil.IMAGE_THUMB_SIZE, 0));
		}
	}

	Location importLocation(final JsonNode json, final String category) throws Exception {
		final Location location = json2location(json);
		if (category != null)
			location.setCategory(category);
		else if (location.getCategory() == null)
			throw new IllegalArgumentException("no relevant category found:\n" + json);
		if (!json.has("photos"))
			throw new IllegalArgumentException("no image in json");
		try {
			String image;
			if (!json.has("photos"))
				throw new IllegalArgumentException("no image in json");
			final String html = externalService.google(
					"place/photo?maxheight=1200&photoreference="
							+ json.get("photos").get(0).get("photo_reference").asText())
					.replace("<A HREF=", "<a href=");
			final Matcher matcher = href.matcher(html);
			if (!matcher.find())
				throw new IllegalArgumentException("no image in html: " + html);
			image = matcher.group(1);
			location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 0));
			if (location.getImage() != null)
				location.setImageList(EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
			repository.save(location);
		} catch (IllegalArgumentException ex) {
			if (ex.getMessage().startsWith("location exists: ")) {
				final Location l = repository.one(Location.class, new BigInteger(ex.getMessage().substring(17)));
				if (!location.getGoogleRating().equals(l.getGoogleRating())
						|| !location.getGoogleRatingTotal().equals(l.getGoogleRatingTotal())
						|| !location.getCategory().equals(l.getCategory())
						|| location.getSubcategories() == null
						|| !location.getSubcategories().equals(l.getSubcategories())) {
					l.setGoogleRating(location.getGoogleRating());
					l.setGoogleRatingTotal(location.getGoogleRatingTotal());
					l.setCategory(location.getCategory());
					l.setSubcategories(location.getSubcategories());
					repository.save(l);
				}
				throw new IllegalArgumentException("location exists");
			} else
				throw ex;
		}
		return location;
	}

	private Location json2location(final JsonNode json) {
		try {
			final Location location = new Location();
			String name = json.get("name").asText();
			final LocationType type = mapFirstRelevantType(json);
			if (type != null) {
				location.setCategory("" + type.category);
				location.setSubcategories(
						type.subCategory == null ? guessSubcategory(type.category, name) : "" + type.subCategory);
			}
			if (name.length() > 100) {
				name = name.substring(0, 101);
				if (name.lastIndexOf(" ") > 10)
					name = name.substring(0, name.lastIndexOf(" "));
			}
			location.setName(name);
			location.setContactId(BigInteger.ONE);
			location.setLatitude((float) json.get("geometry").get("location").get("lat").asDouble());
			location.setLongitude((float) json.get("geometry").get("location").get("lng").asDouble());
			location.setAddress(json.get("vicinity").asText().replace(", ", "\n"));
			if (json.has("rating"))
				location.setGoogleRating((float) json.get("rating").asDouble());
			if (json.has("user_ratings_total"))
				location.setGoogleRatingTotal(json.get("user_ratings_total").asInt());
			return location;
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}

	private String guessSubcategory(final int type, String name) {
		if (type == 2) {
			name = name.toLowerCase();
			final String[] cats = {
					"thai",
					"vietnam",
					"itali",
					"french|französisch",
					"japan",
					"bavaria|bayer|bräu",
					"german|deutsch",
					"spani",
					"afghan",
					"persian",
					"lebanes|libanesisch",
					"chinese",
					"fish|fisch",
					"meat|fleisch",
					"vegetari",
					"vegan",
					"pizza",
					"pasta",
					"barbecue|grill",
					"fast",
					"brunch",
					"sport",
					"beergarden|biergarten",
					"terrace|terasse",
					"coffee|cafe",
					"dessert|kuchen",
					"burger|mcdonald"
			};
			for (int i = 0; i < cats.length; i++) {
				if (name.matches("(.*" + cats[i].replace("|", ".*)|(.*") + ".*)"))
					return "" + i;
			}
		}
		return null;
	}
}
