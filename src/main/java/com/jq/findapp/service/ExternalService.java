package com.jq.findapp.service;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ExternalService {
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
