package com.jq.findapp.service;

import java.io.InputStream;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
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
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.GeoLocationProcessor;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ExternalService {
	private static String STORAGE_PREFIX = "google-address-";
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.chatGPT.key}")
	private String chatGpt;

	public synchronized String google(final String param) {
		final String label = STORAGE_PREFIX + param.hashCode();
		final QueryParams params = new QueryParams(Query.misc_listStorage);
		params.setSearch("storage.label='" + label + "'");
		final Result result = this.repository.list(params);
		if (result.size() > 0 && !Strings.isEmpty(result.get(0).get("storage.storage"))) {
			final String json = (String) result.get(0).get("storage.storage");
			if (!json.contains("OVER_QUERY_LIMIT") && !json.contains("not authorized to use this API key"))
				return json;
		}
		final String value = WebClient.create("https://maps.googleapis.com/maps/api/" + param
				+ (param.contains("?") ? "&" : "?") + "key=" + this.googleKey).get().retrieve().toEntity(String.class)
				.block().getBody();
		if (value != null && value.startsWith("{") && value.endsWith("}")) {
			final Storage storage = result.size() == 0 ? new Storage()
					: this.repository.one(Storage.class, (BigInteger) result.get(0).get("storage.id"));
			storage.setLabel(label);
			storage.setStorage(value);
			this.repository.save(storage);
		}
		return value;
	}

	public List<GeoLocation> convertAddress(final JsonNode address) {
		if ("OK".equals(address.get("status").asText()) && address.get("results") != null) {
			final List<GeoLocation> list = new ArrayList<>();
			for (int i = 0; i < address.get("results").size(); i++) {
				JsonNode data = address.get("results").get(i).get("address_components");
				final GeoLocation geoLocation = new GeoLocation();
				if (data != null) {
					for (int i2 = 0; i2 < data.size(); i2++) {
						if (data.get(i2) != null) {
							final String type = data.get(i2).has("types") ? data.get(i2).get("types").get(0).asText()
									: "";
							if (geoLocation.getStreet() == null && "route".equals(type))
								geoLocation.setStreet(data.get(i2).get("long_name").asText());
							else if (geoLocation.getNumber() == null && "street_number".equals(type))
								geoLocation.setNumber(data.get(i2).get("long_name").asText());
							else if (geoLocation.getTown() == null
									&& ("locality".equals(type) || type.startsWith("administrative_area_level_")))
								geoLocation.setTown(data.get(i2).get("long_name").asText());
							else if (geoLocation.getZipCode() == null && "postal_code".equals(type))
								geoLocation.setZipCode(data.get(i2).get("long_name").asText());
							else if (geoLocation.getCountry() == null && "country".equals(type))
								geoLocation.setCountry(data.get(i2).get("short_name").asText());
						}
					}
					data = address.get("results").get(0).get("geometry");
					if (data != null) {
						data = data.get("location");
						if (data != null) {
							geoLocation.setLatitude((float) data.get("lat").asDouble());
							geoLocation.setLongitude((float) data.get("lng").asDouble());
							if (geoLocation.getTown() != null)
								try {
									this.repository.save(geoLocation);
								} catch (final Exception e) {
								}
						}
					}
					list.add(geoLocation);
				}
			}
			return list.size() == 0 ? null : list;
		}
		return null;
	}

	public List<GeoLocation> getLatLng(final String town) {
		final QueryParams params = new QueryParams(Query.misc_geoLocation);
		if (town.contains("\n")) {
			final String[] s = town.split("\n");
			String s2 = s[0].trim();
			if (s2.contains(" "))
				params.setSearch("geoLocation.street like '%"
						+ s2.substring(0, s2.lastIndexOf(" ")).trim()
						+ "%' and geoLocation.number like '%"
						+ s2.substring(s2.lastIndexOf(" ") + 1) + "%' and ");
			else
				params.setSearch("geoLocation.street like '%" + s2 + "%' and ");
			s2 = s[s.length - 1].trim();
			if (s2.contains(" "))
				params.setSearch(params.getSearch() + "geoLocation.zipCode like '%"
						+ s2.substring(0, s2.indexOf(" "))
						+ "%' and geoLocation.town like '%"
						+ s2.substring(s2.indexOf(" ") + 1).trim() + "%'");
			else
				params.setSearch(params.getSearch() + "geoLocation.town like '%" + s2 + "%'");
		} else
			params.setSearch("geoLocation.town like '%" + town + "%'");
		final Map<String, Object> persistedAddress = this.repository.one(params);
		if (persistedAddress.get("_id") != null)
			return Arrays.asList(this.repository.one(GeoLocation.class, (BigInteger) persistedAddress.get("_id")));
		final List<GeoLocation> geoLocations = this.convertAddress(
				new ObjectMapper().readTree(
						this.google("geocode/json?components=administrative_area:"
								+ URLEncoder.encode(town, StandardCharsets.UTF_8))));
		if (geoLocations == null)
			this.notificationService.createTicket(TicketType.ERROR, "No google address", town, null);
		return geoLocations;
	}

	public GeoLocation getAddress(final float latitude, final float longitude, boolean exact) {
		final QueryParams params = new QueryParams(Query.misc_listGeoLocation);
		final float roundingFactor = exact ? 0.0005f : 0.005f;
		params.setSearch("geoLocation.latitude<" + (latitude + roundingFactor)
				+ " and geoLocation.latitude>" + (latitude - roundingFactor)
				+ " and geoLocation.longitude<" + (longitude + roundingFactor)
				+ " and geoLocation.longitude>" + (longitude - roundingFactor));
		final Result persistedAddress = this.repository.list(params);
		if (persistedAddress.size() > 0) {
			double distance = Double.MAX_VALUE, d;
			BigInteger id = null;
			for (int i = 0; i < persistedAddress.size(); i++) {
				d = GeoLocationProcessor.distance(latitude, longitude,
						(Float) persistedAddress.get(i).get("geoLocation.latitude"),
						(Float) persistedAddress.get(i).get("geoLocation.longitude"));
				if (d < distance) {
					distance = d;
					id = (BigInteger) persistedAddress.get(i).get("geoLocation.id");
				}
			}
			return this.repository.one(GeoLocation.class, id);
		}
		final List<GeoLocation> geoLocations = this.convertAddress(
				new ObjectMapper().readTree(this.google("geocode/json?latlng=" + latitude + ',' + longitude)));
		if (geoLocations != null)
			return geoLocations.get(0);
		this.notificationService.createTicket(TicketType.ERROR, "No google address", latitude + "\n" + longitude, null);
		return null;
	}

	public String map(final String source, final String destination, final Contact contact) {
		String url = Strings.removeSubdomain(this.repository.one(Client.class, contact.getClientId()).getUrl());
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
		url = url.replace("{destination}", destination) + this.googleKey;
		return Base64.getEncoder().encodeToString(
				WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody());
	}

	public String chatGpt(final String prompt) {
		try (final InputStream in = this.getClass().getResourceAsStream("/template/gpt.json")) {
			final String s = WebClient
					.create("https://api.openai.com/v1/completions")
					.post().accept(MediaType.APPLICATION_JSON)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.header("Authorization", "Bearer " + this.chatGpt)
					.bodyValue(IOUtils.toString(in, StandardCharsets.UTF_8).replace("{prompt}", prompt))
					.retrieve().toEntity(String.class).block().getBody();
			return new ObjectMapper().readTree(s).get("choices").get(0).get("text").asText().trim();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public String publishOnFacebook(final BigInteger clientId, final String message, final String link) {
		final Client client = this.repository.one(Client.class, clientId);
		if (!Strings.isEmpty(client.getFbPageAccessToken()) && !Strings.isEmpty(client.getFbPageId())) {
			final Map<String, String> body = new HashMap<>();
			body.put("message", message);
			body.put("link", Strings.removeSubdomain(client.getUrl()) + link);
			body.put("access_token", client.getFbPageAccessToken());
			final String response = WebClient
					.create("https://graph.facebook.com/v19.0/" + client.getFbPageId() + "/feed")
					.post().bodyValue(body).retrieve()
					.toEntity(String.class).block().getBody();
			if (response == null || !response.contains("\"id\":"))
				this.notificationService.createTicket(TicketType.ERROR, "FB", response, null);
			else
				return new ObjectMapper().readTree(response).get("id").asText();
		}
		return null;
	}
}
