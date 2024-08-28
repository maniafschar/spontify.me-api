package com.jq.findapp.repository.listener;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.LocationFavorite;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.util.Strings;

@Component
public class LocationListener extends AbstractRepositoryListener<Location> {
	@Autowired
	private ExternalService externalService;

	@Override
	public void prePersist(final Location location)
			throws Exception {
		location.setName(sanitizeName(location.getName()));
		location.setUrl(sanitizeUrl(location.getUrl()));
		lookupAddress(location);
		final QueryParams params = new QueryParams(Query.location_list);
		params.setUser(new Contact());
		params.getUser().setId(BigInteger.ZERO);
		params.setSearch(
				"location.zipCode='" + location.getZipCode() + "' and LOWER(location.street)='"
						+ (location.getStreet() == null ? ""
								: location.getStreet().replace("'", "''").toLowerCase().replace("traße", "tr."))
						+ "' and LOWER(location.number)='"
						+ (location.getNumber() == null ? ""
								: location.getNumber().toLowerCase())
						+ "'");
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			if (isNameMatch((String) list.get(i).get("location.name"), location.getName(), true))
				throw new IllegalArgumentException("location exists: " + list.get(i).get("location.id"));
		}
	}

	@Override
	public void preUpdate(final Location location) throws Exception {
		location.setName(sanitizeName(location.getName()));
		if (location.old("url") != null)
			location.setUrl(sanitizeUrl(location.getUrl()));
		if (location.old("address") != null)
			lookupAddress(location);
	}

	@Override
	public void postPersist(final Location location) throws Exception {
		final LocationFavorite locationFavorite = new LocationFavorite();
		locationFavorite.setContactId(location.getContactId());
		locationFavorite.setLocationId(location.getId());
		locationFavorite.setFavorite(true);
		repository.save(locationFavorite);
	}

	private void checkDuplicateLatLon(Location location) {
		final float roundingFactor = 0.0005f;
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch((location.getId() == null ? "" : "location.id<>" + location.getId() + " and ") +
				"LOWER(location.name) like '%" + location.getName().toLowerCase().replace("'", "_").replace(" ", "%")
				+ "%' and location.longitude<"
				+ (location.getLongitude() + roundingFactor)
				+ " and location.longitude>"
				+ (location.getLongitude() - roundingFactor)
				+ " and location.latitude<"
				+ (location.getLatitude() + roundingFactor)
				+ " and location.latitude>"
				+ (location.getLatitude() - roundingFactor));
		final Result list = repository.list(params);
		if (list.size() > 0)
			throw new IllegalArgumentException("location exists: " + list.get(0).get("location.id"));
	}

	private void lookupAddress(final Location location) throws Exception {
		if (location.getLatitude() != null)
			checkDuplicateLatLon(location);
		final JsonNode address = new ObjectMapper().readTree(
				externalService.google("geocode/json?address="
						+ location.getAddress().replaceAll("\n", ", ")));
		if (!"OK".equals(address.get("status").asText()))
			throw new IllegalArgumentException(
					"invalid address:\n" + location.getName() + "\n" + location.getAddress() + "\n"
							+ new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(address));
		final JsonNode result = address.get("results").get(0);
		JsonNode n = result.get("geometry").get("location");
		final GeoLocation geoLocation = externalService.convertAddress(address).get(0);
		location.setAddress(geoLocation.getFormatted());
		location.setCountry(geoLocation.getCountry());
		location.setTown(geoLocation.getTown());
		location.setZipCode(geoLocation.getZipCode());
		location.setStreet(geoLocation.getStreet());
		location.setNumber(geoLocation.getNumber());
		if (!Strings.isEmpty(geoLocation.getStreet())) {
			if (location.getStreet().contains("traße")) {
				final String street = location.getStreet().replace("traße", "tr.");
				location.setAddress(location.getAddress().replace(location.getStreet(), street));
				location.setStreet(street);
			}
		}
		if (location.getLongitude() == null || location.getLongitude() == 0
				|| location.getLatitude() == null || location.getLatitude() == 0) {
			location.setLatitude(geoLocation.getLatitude());
			location.setLongitude(geoLocation.getLongitude());
			checkDuplicateLatLon(location);
		}
		n = result.get("address_components");
		String s = "";
		for (int i = 0; i < n.size(); i++) {
			if (!location.getAddress().contains(n.get(i).get("long_name").asText()))
				s += '\n' + n.get(i).get("long_name").asText();
		}
		location.setAddress2(s.trim());
	}

	private boolean isNameMatch(String name1, String name2, final boolean tryReverse) {
		name1 = prepare(name1);
		name2 = prepare(name2);
		final String[] name1Splitted = name1.split(" ");
		int letters = 0, words = 0;
		for (int i = 0; i < name1Splitted.length; i++) {
			if (name2.contains(name1Splitted[i])) {
				letters += name1Splitted[i].length();
				words++;
			}
		}
		if (letters > name2.length() * 0.75 || name1Splitted.length > 2 && words > name1Splitted.length * 0.6)
			return true;
		if (tryReverse)
			return isNameMatch(name2, name1, false);
		return false;
	}

	private String sanitizeName(String name) {
		if (name != null) {
			while (name.contains("  "))
				name = name.replace("  ", " ");
			name = name.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
		}
		return name;
	}

	private String sanitizeUrl(String url) {
		if (!Strings.isEmpty(url)) {
			if (!url.contains("://") && url.contains(".")) {
				try {
					WebClient.create("https://" + url.trim())
							.get()
							.retrieve()
							.bodyToMono(String.class).block();
					url = "https://" + url.trim();
				} catch (Exception ex) {
					url = "http://" + url.trim();
				}
			} else
				url = url.trim();
		}
		return url.toLowerCase();
	}

	private String prepare(String s) {
		while (s.contains("  "))
			s = s.replaceAll("  ", " ");
		return s.trim().toLowerCase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
	}
}
