package com.jq.findapp.service;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
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

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static final LocationType[] TYPES = {
			new LocationType("accounting", null),
			new LocationType("airport", "3"),
			new LocationType("amusement_park", "3"),
			new LocationType("aquarium", "3"),
			new LocationType("art_gallery", "3"),
			new LocationType("atm", null),
			new LocationType("bakery", "0"),
			new LocationType("bank", "0"),
			new LocationType("bar", "4"),
			new LocationType("beauty_salon", "0"),
			new LocationType("bicycle_store", "0"),
			new LocationType("book_store", "0"),
			new LocationType("bowling_alley", "3"),
			new LocationType("bus_station", null),
			new LocationType("cafe", "2"),
			new LocationType("campground", "3"),
			new LocationType("car_dealer", "0"),
			new LocationType("car_rental", null),
			new LocationType("car_repair", null),
			new LocationType("car_wash", null),
			new LocationType("casino", "4"),
			new LocationType("cemetery", "3"),
			new LocationType("church", "1"),
			new LocationType("city_hall", "3"),
			new LocationType("clothing_store", "0"),
			new LocationType("convenience_store", "0"),
			new LocationType("courthouse", "3"),
			new LocationType("dentist", "0"),
			new LocationType("department_store", "0"),
			new LocationType("doctor", "0"),
			new LocationType("drugstore", "0"),
			new LocationType("electrician", null),
			new LocationType("electronics_store", null),
			new LocationType("embassy", "3"),
			new LocationType("fire_station", "3"),
			new LocationType("florist", "0"),
			new LocationType("funeral_home", null),
			new LocationType("furniture_store", "0"),
			new LocationType("gas_station", "0"),
			new LocationType("grocery_or_supermarket", "0"),
			new LocationType("gym", "5"),
			new LocationType("hair_care", "0"),
			new LocationType("hardware_store", "0"),
			new LocationType("hindu_temple", "1"),
			new LocationType("home_goods_store", "0"),
			new LocationType("hospital", null),
			new LocationType("insurance_agency", null),
			new LocationType("jewelry_store", "0"),
			new LocationType("laundry", null),
			new LocationType("lawyer", null),
			new LocationType("library", "1"),
			new LocationType("light_rail_station", null),
			new LocationType("liquor_store", "0"),
			new LocationType("local_government_office", null),
			new LocationType("locksmith", null),
			new LocationType("lodging", "3"),
			new LocationType("meal_delivery", "2"),
			new LocationType("meal_takeaway", "2"),
			new LocationType("mosque", "1"),
			new LocationType("movie_rental", null),
			new LocationType("movie_theater", "1"),
			new LocationType("moving_company", null),
			new LocationType("museum", "1"),
			new LocationType("night_club", "5"),
			new LocationType("painter", null),
			new LocationType("park", "3"),
			new LocationType("parking", null),
			new LocationType("pet_store", "0"),
			new LocationType("pharmacy", "0"),
			new LocationType("physiotherapist", "5"),
			new LocationType("plumber", null),
			new LocationType("police", null),
			new LocationType("post_office", "0"),
			new LocationType("primary_school", null),
			new LocationType("real_estate_agency", null),
			new LocationType("restaurant", "2"),
			new LocationType("roofing_contractor", null),
			new LocationType("rv_park", null),
			new LocationType("school", null),
			new LocationType("secondary_school", null),
			new LocationType("shoe_store", "0"),
			new LocationType("shopping_mall", "0"),
			new LocationType("spa", "3"),
			new LocationType("stadium", "3"),
			new LocationType("storage", null),
			new LocationType("store", "0"),
			new LocationType("subway_station", null),
			new LocationType("supermarket", "0"),
			new LocationType("synagogue", "1"),
			new LocationType("taxi_stand", null),
			new LocationType("tourist_attraction", "3"),
			new LocationType("train_station", null),
			new LocationType("transit_station", null),
			new LocationType("travel_agency", null),
			new LocationType("university", "1"),
			new LocationType("veterinary_care", "0"),
			new LocationType("zoo", "3")
	};

	private static class LocationType {
		private final String google;
		private final String category;

		private LocationType(String google, String category) {
			this.google = google;
			this.category = category;
		}
	}

	@Async
	public void lookup(float latitude, float longitude) throws Exception {
		final float lat = ((int) (latitude * roundingFactor) / roundingFactor);
		final float lon = ((int) (longitude * roundingFactor) / roundingFactor);
		final QueryParams params = new QueryParams(Query.misc_listTicket);
		for (LocationType type : TYPES) {
			final String s = lat + "\n" + lon + "\n" + type.google;
			params.setSearch("ticket.subject='import' and ticket.note='" + s + "' and ticket.type='"
					+ TicketType.LOCATION.name() + "'");
			if (type.category != null && repository.list(params).size() == 0) {
				lookup(latitude, longitude, type);
				notificationService.createTicket(TicketType.LOCATION, "import", s, adminId);
			}
		}
	}

	public boolean importLocation(BigInteger ticketId, String category) throws Exception {
		final Ticket ticket = repository.one(Ticket.class, ticketId);
		final boolean imported = importLocation(new String(Attachment.getFile(ticket.getNote())), category);
		repository.delete(ticket);
		return imported;
	}

	private void lookup(float latitude, float longitude, LocationType type) throws Exception {
		final ObjectMapper om = new ObjectMapper();
		final JsonNode addresses = om.readTree(externalService.google(
				"place/nearbysearch/json?radius=600&sensor=false&location="
						+ latitude + "," + longitude + "&type=" + type.google,
				adminId));
		if ("OK".equals(addresses.get("status").asText())) {
			addresses.get("results").elements().forEachRemaining(
					e -> {
						if (e.has("photos") &&
								(!e.has("permanently_closed") || !e.get("permanently_closed").asBoolean()) &&
								e.has("rating") && e.get("rating").asDouble() > 3.5) {
							try {
								final String json = om.writerWithDefaultPrettyPrinter().writeValueAsString(e),
										jsonLower = json.toLowerCase();
								if (jsonLower.contains("sex") || jsonLower.contains("domina")
										|| jsonLower.contains("bordel") || !e.has("types")
										|| e.get("types").size() == 0
										|| mapType(e.get("types").get(0).asText()) == null)
									notificationService.createTicket(TicketType.LOCATION,
											type.category + " " + e.get("name").asText(), json, adminId);
								else
									importLocation(json, mapType(e.get("types").get(0).asText()));
							} catch (Exception ex) {
								throw new RuntimeException(ex);
							}
						}
					});
		}
	}

	private String mapType(String typeGoogle) {
		for (LocationType type : TYPES) {
			if (typeGoogle.equals(type.google))
				return type.category;
		}
		return null;
	}

	private boolean importLocation(String json, String category) {
		final Location location = new Location();
		try {
			final JsonNode address = new ObjectMapper().readTree(json);
			location.setContactId(adminId);
			location.setName(address.get("name").asText());
			location.setParkingOption("3");
			location.setCategory(category);
			location.setLatitude((float) address.get("geometry").get("location").get("lat").asDouble());
			location.setLongitude((float) address.get("geometry").get("location").get("lng").asDouble());
			location.setAddress(address.get("vicinity").asText().replaceAll(", ", "\n"));
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("LOWER(location.name) like '%"
					+ location.getName().toLowerCase().replace("'", "''").replace(' ', '%')
					+ "%' and (LOWER(location.address) like '%"
					+ location.getAddress().toLowerCase().replace("traße", "tr.").replace('\n', '%')
					+ "%' and location.category='"
					+ location.getCategory()
					+ "' or location.latitude like '"
					+ ((int) location.getLatitude().floatValue() * roundingFactor) / roundingFactor
					+ "%' and location.longitude like '"
					+ ((int) location.getLongitude().floatValue() * roundingFactor) / roundingFactor + "%')");
			if (repository.list(params).size() == 0) {
				if (address.has("photos")) {
					final String html = externalService.google(
							"place/photo?maxheight=1200&photoreference="
									+ address.get("photos").get(0).get("photo_reference").asText(),
							adminId).replace("<A HREF=", "<a href=");
					final Matcher matcher = href.matcher(html);
					if (matcher.find()) {
						location.setImage(EntityUtil.getImage(matcher.group(1), EntityUtil.IMAGE_SIZE));
						if (location.getImage() != null)
							location.setImageList(EntityUtil.getImage(matcher.group(1), EntityUtil.IMAGE_THUMB_SIZE));
					}
				}
				repository.save(location);
				return true;
			}
		} catch (Exception ex) {
			if (!ex.getMessage().contains("Failed on image") && !ex.getMessage().contains("Location exists")
					&& !Strings.stackTraceToString(ex).contains("Duplicate entry"))
				notificationService.createTicket(TicketType.LOCATION,
						category + " " + location.getName(), json, adminId);
		}
		return false;
	}
}