package com.jq.findapp.service;

import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.ContactGeoLocationHistory;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ExternalService {
	@Autowired
	private Repository repository;

	@Value("${app.google.key}")
	private String googleKey;

	public String google(String param) {
		return WebClient
				.create("https://maps.googleapis.com/maps/api/" + param + (param.contains("?") ? "&" : "?") + "key="
						+ googleKey)
				.get().retrieve().toEntity(String.class).block().getBody();
	}

	public Address convertGoogleAddress(JsonNode data) {
		if ("OK".equals(data.get("status").asText()) && data.get("results") != null) {
			data = data.get("results").get(0).get("address_components");
			final Address address = new Address();
			for (int i = 0; i < data.size(); i++) {
				if (address.street == null && "route".equals(data.get(i).get("types").get(0).asText()))
					address.street = data.get(i).get("long_name").asText();
				else if (address.number == null && "street_number".equals(data.get(i).get("types").get(0).asText()))
					address.number = data.get(i).get("long_name").asText();
				else if (address.town == null &&
						("locality".equals(data.get(i).get("types").get(0).asText()) ||
								data.get(i).get("types").get(0).asText().startsWith("administrative_area_level_")))
					address.town = data.get(i).get("long_name").asText();
				else if (address.zipCode == null && "postal_code".equals(data.get(i).get("types").get(0).asText()))
					address.zipCode = data.get(i).get("long_name").asText();
				else if (address.country == null && "country".equals(data.get(i).get("types").get(0).asText()))
					address.country = data.get(i).get("short_name").asText();
			}
			return address;
		}
		return null;
	}

	public Address googleAddress(float latitude, float longitude) throws JsonMappingException, JsonProcessingException {
		final QueryParams params = new QueryParams(Query.contact_geoLocation);
		params.setSearch("contactGeoLocationHistory.latitude like '" + round(latitude)
				+ "%' and contactGeoLocationHistory.longitude like '" + round(longitude) + "%'");
		final Result persistedAddress = repository.list(params);
		final Address address;
		if (persistedAddress.size() > 0) {
			final ContactGeoLocationHistory contactGeoLocationHistory = repository.one(ContactGeoLocationHistory.class,
					(BigInteger) persistedAddress.get(0).get("_id"));
			address = new Address();
			address.street = contactGeoLocationHistory.getStreet();
			address.town = contactGeoLocationHistory.getTown();
			address.zipCode = contactGeoLocationHistory.getZipCode();
			address.country = contactGeoLocationHistory.getCountry();
		} else
			address = convertGoogleAddress(new ObjectMapper()
					.readTree(google("geocode/json?latlng=" + latitude + ',' + longitude)));
		return address;
	}

	private String round(float d) {
		final int digits = 5 + 1;
		String s = "" + d;
		if (s.length() - digits >= s.indexOf('.'))
			s = s.substring(0, s.indexOf('.') + digits);
		return s;
	}

	public static class Address {
		public String street;
		public String number;
		public String town;
		public String zipCode;
		public String country;

		public String getFormatted() {
			String s = "";
			if (street != null)
				s += street + (number == null ? "" : " " + number);
			if (zipCode == null) {
				if (town != null)
					s += "\n" + town;
			} else {
				s += "\n" + zipCode;
				if (town != null)
					s += " " + town;
			}
			return s;
		}
	}
}
