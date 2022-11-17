package com.jq.findapp.service;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
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

@Service
public class ImportLocationsService {
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
			new LocationType("bank", null),
			new LocationType("bar", "4"),
			new LocationType("beauty_salon", null),
			new LocationType("bicycle_store", null),
			new LocationType("book_store", "0"),
			new LocationType("bowling_alley", "3"),
			new LocationType("bus_station", null),
			new LocationType("cafe", "2"),
			new LocationType("campground", null),
			new LocationType("car_dealer", null),
			new LocationType("car_rental", null),
			new LocationType("car_repair", null),
			new LocationType("car_wash", null),
			new LocationType("casino", "4"),
			new LocationType("cemetery", "3"),
			new LocationType("church", "1"),
			new LocationType("city_hall", "3"),
			new LocationType("clothing_store", "0"),
			new LocationType("convenience_store", null),
			new LocationType("courthouse", "3"),
			new LocationType("dentist", null),
			new LocationType("department_store", "0"),
			new LocationType("doctor", null),
			new LocationType("drugstore", null),
			new LocationType("electrician", null),
			new LocationType("electronics_store", null),
			new LocationType("embassy", "3"),
			new LocationType("fire_station", null),
			new LocationType("florist", null),
			new LocationType("funeral_home", null),
			new LocationType("furniture_store", null),
			new LocationType("gas_station", null),
			new LocationType("gym", "5"),
			new LocationType("hair_care", null),
			new LocationType("hardware_store", null),
			new LocationType("hindu_temple", null),
			new LocationType("home_goods_store", "0"),
			new LocationType("hospital", null),
			new LocationType("insurance_agency", null),
			new LocationType("jewelry_store", "0"),
			new LocationType("laundry", null),
			new LocationType("lawyer", null),
			new LocationType("library", "1"),
			new LocationType("light_rail_station", null),
			new LocationType("liquor_store", null),
			new LocationType("local_government_office", null),
			new LocationType("locksmith", null),
			new LocationType("lodging", null),
			new LocationType("meal_delivery", null),
			new LocationType("meal_takeaway", null),
			new LocationType("mosque", "1"),
			new LocationType("movie_rental", null),
			new LocationType("movie_theater", "1"),
			new LocationType("moving_company", null),
			new LocationType("museum", "1"),
			new LocationType("night_club", null),
			new LocationType("painter", null),
			new LocationType("park", "3"),
			new LocationType("parking", null),
			new LocationType("pet_store", null),
			new LocationType("pharmacy", null),
			new LocationType("physiotherapist", null),
			new LocationType("plumber", null),
			new LocationType("police", null),
			new LocationType("post_office", null),
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
			new LocationType("store", null),
			new LocationType("subway_station", null),
			new LocationType("supermarket", null),
			new LocationType("synagogue", "1"),
			new LocationType("taxi_stand", null),
			new LocationType("tourist_attraction", "3"),
			new LocationType("train_station", null),
			new LocationType("transit_station", null),
			new LocationType("travel_agency", null),
			new LocationType("university", "1"),
			new LocationType("veterinary_care", null),
			new LocationType("zoo", "3") };

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
		final float roundingFactor = 1000f;
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

	private void lookup(float latitude, float longitude, LocationType type) throws Exception {
		final ObjectMapper om = new ObjectMapper();
		final JsonNode addresses = om.readTree(externalService.google(
				"place/nearbysearch/json?radius=1000&sensor=false&location="
						+ latitude + "," + longitude + "&type=" + type.google,
				adminId));
		if ("OK".equals(addresses.get("status").asText())) {
			addresses.get("results").elements().forEachRemaining(
					e -> {
						if (e.has("photos") &&
								(!e.has("permanently_closed") || !e.get("permanently_closed").asBoolean())) {
							try {
								notificationService.createTicket(TicketType.LOCATION,
										type.category + " " + e.get("name").asText(),
										om.writerWithDefaultPrettyPrinter().writeValueAsString(e), adminId);
							} catch (JsonProcessingException ex) {
								throw new RuntimeException(ex);
							}
						}
					});
		}
	}

	public void importLocation(BigInteger ticketId) throws Exception {
		final Ticket ticket = repository.one(Ticket.class, ticketId);
		final JsonNode address = new ObjectMapper().readTree(new String(Attachment.getFile(ticket.getNote())));
		final Location location = new Location();
		location.setContactId(adminId);
		location.setName(address.get("name").asText());
		location.setParkingOption("3");
		location.setCategory(ticket.getSubject().split(" ")[0]);
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
		location.setAddress(address.get("vicinity").asText().replaceAll(", ", "\n"));
		repository.save(location);
	}
}