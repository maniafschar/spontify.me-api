package com.jq.findapp.service;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class ExternalService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.chatGPT.key}")
	private String chatGpt;

	@Value("${app.admin.id}")
	protected BigInteger adminId;

	public String google(String param, BigInteger user) {
		final String result = WebClient
				.create("https://maps.googleapis.com/maps/api/" + param + (param.contains("?") ? "&" : "?")
						+ "key=" + googleKey)
				.get().retrieve().toEntity(String.class).block().getBody();
		try {
			final ObjectMapper om = new ObjectMapper();
			notificationService.createTicket(TicketType.GOOGLE, param,
					om.writerWithDefaultPrettyPrinter().writeValueAsString(om.readTree(result)), user);
		} catch (Exception e) {
			notificationService.createTicket(TicketType.GOOGLE, param, result, user);
		}
		return result;
	}

	public GeoLocation convertAddress(JsonNode address) {
		if ("OK".equals(address.get("status").asText()) && address.get("results") != null) {
			JsonNode data = address.get("results").get(0).get("address_components");
			final GeoLocation geoLocation = new GeoLocation();
			for (int i = 0; i < data.size(); i++) {
				if (geoLocation.getStreet() == null && "route".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setStreet(data.get(i).get("long_name").asText());
				else if (geoLocation.getNumber() == null
						&& "street_number".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setNumber(data.get(i).get("long_name").asText());
				else if (geoLocation.getTown() == null &&
						("locality".equals(data.get(i).get("types").get(0).asText()) ||
								data.get(i).get("types").get(0).asText().startsWith("administrative_area_level_")))
					geoLocation.setTown(data.get(i).get("long_name").asText());
				else if (geoLocation.getZipCode() == null
						&& "postal_code".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setZipCode(data.get(i).get("long_name").asText());
				else if (geoLocation.getCountry() == null && "country".equals(data.get(i).get("types").get(0).asText()))
					geoLocation.setCountry(data.get(i).get("short_name").asText());
			}
			data = address.get("results").get(0).get("geometry");
			if (data != null) {
				data = data.get("location");
				if (data != null) {
					geoLocation.setLatitude((float) data.get("lat").asDouble());
					geoLocation.setLongitude((float) data.get("lng").asDouble());
				}
			}
			return geoLocation;
		}
		return null;
	}

	public GeoLocation getLatLng(String town, BigInteger user) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_geoLocation);
		params.setSearch("geoLocation.town like '%" + town + "%'");
		final Map<String, Object> persistedAddress = repository.one(params);
		if (persistedAddress.get("_id") != null)
			return repository.one(GeoLocation.class, (BigInteger) persistedAddress.get("_id"));
		final GeoLocation geoLocation = convertAddress(
				new ObjectMapper().readTree(
						google("geocode/json?address=" + URLEncoder.encode(town, StandardCharsets.UTF_8), user)));
		if (geoLocation != null)
			repository.save(geoLocation);
		else
			notificationService.createTicket(TicketType.ERROR, "No google address", town, user);
		return geoLocation;
	}

	public GeoLocation getAddress(float latitude, float longitude, BigInteger user) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_geoLocation);
		final float roundingFactor = 10000f;
		params.setSearch("geoLocation.latitude like '" + ((int) (latitude * roundingFactor) / roundingFactor)
				+ "%' and geoLocation.longitude like '" + ((int) (longitude * roundingFactor) / roundingFactor) + "%'");
		final Map<String, Object> persistedAddress = repository.one(params);
		if (persistedAddress.get("_id") != null)
			return repository.one(GeoLocation.class, (BigInteger) persistedAddress.get("_id"));
		final GeoLocation geoLocation = convertAddress(
				new ObjectMapper().readTree(google("geocode/json?latlng=" + latitude + ',' + longitude, user)));
		if (geoLocation != null) {
			geoLocation.setLongitude(longitude);
			geoLocation.setLatitude(latitude);
			repository.save(geoLocation);
		} else
			notificationService.createTicket(TicketType.ERROR, "No google address", latitude + "\n" + longitude, user);
		return geoLocation;
	}

	public String map(String source, String destination, Contact contact) {
		String url = repository.one(Client.class, contact.getClientId()).getUrl();
		if (source == null || source.length() == 0)
			url = "https://maps.googleapis.com/maps/api/staticmap?{destination}&markers=icon:" + url
					+ "/images/mapMe.png|shadow:false|{destination}&scale=2&size=200x200&maptype=roadmap&key=";
		else {
			url = "https://maps.googleapis.com/maps/api/staticmap?{source}|{destination}&markers=icon:"
					+ url
					+ "/images/mapMe.png|shadow:false|{source}&markers=icon:" + url
					+ "/images/mapLoc.png|shadow:false|{destination}&scale=2&size=600x200&maptype=roadmap&sensor=true&key=";
			url = url.replace("{source}", source);
		}
		url = url.replace("{destination}", destination) + googleKey;
		return Base64.getEncoder().encodeToString(
				WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody());
	}

	public String chatGpt(String prompt) throws Exception {
		final String s = WebClient
				.create("https://api.openai.com/v1/completions")
				.post().accept(MediaType.APPLICATION_JSON)
				.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
				.header("Authorization", "Bearer " + chatGpt)
				.bodyValue(IOUtils.toString(getClass().getResourceAsStream("/template/gpt.json"),
						StandardCharsets.UTF_8).replace("{prompt}", prompt))
				.retrieve().toEntity(String.class).block().getBody();
		notificationService.createTicket(TicketType.ERROR, "gpt", prompt + "\n\n" + s, adminId);
		return new ObjectMapper().readTree(s).get("choices").get(0).get("text").asText().trim();
	}
}