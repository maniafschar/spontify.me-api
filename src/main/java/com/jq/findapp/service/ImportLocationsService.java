package com.jq.findapp.service;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;

@Service
public class ImportLocationsService {
	private final float roundingFactor = 1000f;
	private final Pattern href = Pattern.compile("href=\"([^\"]*)\"");

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	Repository repository;

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
	public void lookup(final float latitude, final float longitude) throws Exception {
		final float lat = ((int) (latitude * roundingFactor) / roundingFactor);
		final float lon = ((int) (longitude * roundingFactor) / roundingFactor);
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		final String s = lat + "\n" + lon;
		params.setSearch("ticket.subject='import' and ticket.note like '" + s +
				"%' and ticket.type='" + TicketType.LOCATION.name() + "'");
		if (repository.list(params).size() == 0) {
			final String importResult = retrieveLocations(latitude, longitude);
			notificationService.createTicket(TicketType.LOCATION, "import",
					s + (importResult == null ? "" : "\n" + importResult), null);
		}
	}

	public String importLocation(final BigInteger ticketId, final String category) throws Exception {
		final Ticket ticket = repository.one(Ticket.class, ticketId);
		String result = null;
		try {
			importLocation(new ObjectMapper().readTree(new String(Attachment.getFile(ticket.getNote()))), category);
			repository.delete(ticket);
		} catch (final IllegalArgumentException ex) {
			result = ex.getMessage();
		} catch (final Exception ex) {
			result = Strings.stackTraceToString(ex);
		}
		return result;
	}

	private String retrieveLocations(final float latitude, final float longitude) throws Exception {
		final ObjectMapper om = new ObjectMapper();
		JsonNode json = om.readTree(externalService.google(
				"place/nearbysearch/json?radius=600&sensor=false&location="
						+ latitude + "," + longitude));
		if ("OK".equals(json.get("status").asText())) {
			String result = importLocations(json.get("results").elements());
			while (json.has("next_page_token")) {
				json = om.readTree(externalService.google(
						"place/nearbysearch/json?pagetoken=" + json.get("next_page_token").asText()));
				if ("OK".equals(json.get("status").asText()))
					result += "\n" + importLocations(json.get("results").elements());
				else
					break;
			}
			return result;
		}
		return null;
	}

	private String importLocations(final Iterator<JsonNode> result) {
		final ObjectMapper om = new ObjectMapper();
		int imported = 0, total = 0;
		String town = null;
		Location location;
		while (result.hasNext()) {
			final JsonNode json = result.next();
			total++;
			location = json2location(json);
			if (json.has("photos") && mapFirstRelevantType(json) != null && exists(location) == null &&
					(!json.has("permanently_closed") || !json.get("permanently_closed").asBoolean()) &&
					json.has("rating") && json.get("rating").asDouble() > 3) {
				try {
					final String jsonLower = om.writeValueAsString(json).toLowerCase();
					if (jsonLower.contains("sex") || jsonLower.contains("domina") || jsonLower.contains("bordel")) {
						notificationService.createTicket(TicketType.LOCATION,
								location.getCategory() + " " + location.getName(),
								om.writerWithDefaultPrettyPrinter().writeValueAsString(json), null);
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
									|| (!ex.getMessage().contains("no image")
											// && !ex.getMessage().contains("invalid address")
											&& !ex.getMessage().contains("location exists")
											&& !Strings.stackTraceToString(ex).toLowerCase()
													.contains("duplicate entry")))) {
								notificationService.createTicket(TicketType.LOCATION,
										location.getCategory() + " " + location.getName(),
										om.writerWithDefaultPrettyPrinter().writeValueAsString(json), null);
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

	Location importLocation(final JsonNode json, final String category) throws Exception {
		final Location location = json2location(json);
		if (category != null)
			location.setCategory(category);
		else if (location.getCategory() == null)
			throw new IllegalArgumentException("no relevant category found:\n" + json);
		String image;
		try {
			if (!json.has("photos"))
				throw new IllegalArgumentException("no image in json");
			final BigInteger id = exists(location);
			if (id != null) {
				final Location l = repository.one(Location.class, id);
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
			}
			final String html = externalService.google(
					"place/photo?maxheight=1200&photoreference="
							+ json.get("photos").get(0).get("photo_reference").asText())
					.replace("<A HREF=", "<a href=");
			final Matcher matcher = href.matcher(html);
			if (!matcher.find())
				throw new IllegalArgumentException("no image in html: " + html);
			image = matcher.group(1);
		} catch (final JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		location.setImage(EntityUtil.getImage(image, EntityUtil.IMAGE_SIZE, 0));
		if (location.getImage() != null)
			location.setImageList(EntityUtil.getImage(image, EntityUtil.IMAGE_THUMB_SIZE, 0));
		repository.save(location);
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

	private BigInteger exists(final Location location) {
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch("LOWER(location.name) like '%"
				+ location.getName().toLowerCase().replace("'", "''").replace(' ', '%')
				+ "%' and location.category='"
				+ location.getCategory()
				+ "' and (LOWER(location.address) like '%"
				+ location.getAddress().toLowerCase().replace("traße", "tr.").replace('\n', '%').replace("'", "''")
				+ "%' or location.latitude like '"
				+ ((int) (location.getLatitude().floatValue() * roundingFactor)) / roundingFactor
				+ "%' and location.longitude like '"
				+ ((int) (location.getLongitude().floatValue() * roundingFactor)) / roundingFactor + "%')");
		final Result result = repository.list(params);
		return result.size() > 0 ? (BigInteger) result.get(0).get("location.id") : null;
	}
}