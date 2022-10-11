package com.jq.findapp.repository.listener;

import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
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

@Component
public class LocationListener extends AbstractRepositoryListener {
	private static ExternalService externalService;

	@Autowired
	private void setExternalService(ExternalService externalService) {
		LocationListener.externalService = externalService;
	}

	@PrePersist
	public void prePersist(Location location)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		lookupAddress(location);
		final QueryParams params = new QueryParams(Query.location_list);
		location.getCategory();
		location.getName();
		params.setUser(repository.one(Contact.class, location.getContactId()));
		params.setSearch(
				"REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(location.address),'''',''),'\\n',''),'\\r',''),'\\t',''),' ','')='"
						+ location.getAddress().toLowerCase().replaceAll("'", "").replaceAll("\n", "")
								.replaceAll("\r", "").replaceAll("\t", "").replaceAll(" ", "")
						+ "'");
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			if (isNameMatch((String) list.get(i).get("location.name"), location.getName(), true))
				throw new IllegalAccessException("Location exists");
		}
	}

	@PreUpdate
	public void preUpdate(Location location) throws Exception {
		if (location.old("address") != null)
			lookupAddress(location);
	}

	static void postPersist(Location location) throws Exception {
		final LocationFavorite locationFavorite = new LocationFavorite();
		locationFavorite.setContactId(location.getContactId());
		locationFavorite.setLocationId(location.getId());
		locationFavorite.setFavorite(true);
		repository.save(locationFavorite);
	}

	private void lookupAddress(Location location)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		final JsonNode googleAddress = new ObjectMapper().readTree(
				externalService.google("geocode/json?address="
						+ location.getName() + ", " + location.getAddress().replaceAll("\n", ", "),
						location.getContactId()));
		if (!"OK".equals(googleAddress.get("status").asText()))
			throw new IllegalAccessException("Invalid address:\n"
					+ new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(googleAddress));
		final JsonNode result = googleAddress.get("results").get(0);
		JsonNode n = result.get("geometry").get("location");
		final GeoLocation geoLocation = externalService.convertGoogleAddress(googleAddress);
		location.setAddress(geoLocation.getFormatted());
		location.setCountry(geoLocation.getCountry());
		location.setTown(geoLocation.getTown());
		location.setZipCode(geoLocation.getZipCode());
		location.setStreet(geoLocation.getStreet());
		location.setNumber(geoLocation.getNumber());
		if (geoLocation.getStreet() != null && geoLocation.getStreet().trim().length() > 0) {
			location.setLatitude(geoLocation.getLatitude());
			location.setLongitude(geoLocation.getLongitude());
		}
		n = result.get("address_components");
		String s = "";
		for (int i = 0; i < n.size(); i++) {
			if (!location.getAddress().contains(n.get(i).get("long_name").asText()))
				s += '\n' + n.get(i).get("long_name").asText();
		}
		location.setAddress2(s.trim());
	}

	private boolean isNameMatch(String name1, String name2, boolean tryReverse) {
		name1 = name1.trim().toLowerCase();
		name2 = name2.trim().toLowerCase();
		while (name1.contains("  "))
			name1 = name1.replaceAll("  ", " ");
		final String[] n = name1.split(" ");
		int count = 0;
		for (int i = 0; i < n.length; i++) {
			if (name2.contains(n[i]))
				count++;
		}
		if (count == n.length)
			return true;
		if (tryReverse)
			return isNameMatch(name2, name1, false);
		return false;
	}
}