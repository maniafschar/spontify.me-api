package com.jq.findapp.service;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Ip;
import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Ticket.Type;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class ExternalService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.google.key}")
	private String googleKey;

	@Value("${app.url.lookupip}")
	private String lookupip;

	public String google(String param) {
		final String result = WebClient
				.create("https://maps.googleapis.com/maps/api/" + param + (param.contains("?") ? "&" : "?")
						+ "key=" + googleKey)
				.get().retrieve().toEntity(String.class).block().getBody();
		try {
			final ObjectMapper om = new ObjectMapper();
			notificationService.createTicket(Type.GOOGLE, param,
					om.writerWithDefaultPrettyPrinter().writeValueAsString(om.readTree(result)));
		} catch (Exception e) {
			notificationService.createTicket(Type.GOOGLE, param, result);
		}
		return result;
	}

	public GeoLocation convertGoogleAddress(JsonNode google) {
		if ("OK".equals(google.get("status").asText()) && google.get("results") != null) {
			JsonNode data = google.get("results").get(0).get("address_components");
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
			data = google.get("results").get(0).get("geometry");
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

	public GeoLocation googleAddress(float latitude, float longitude) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_geoLocation);
		params.setSearch("geoLocation.latitude like '" + round(latitude)
				+ "%' and geoLocation.longitude like '" + round(longitude) + "%'");
		final Result persistedAddress = repository.list(params);
		if (persistedAddress.size() > 0)
			return repository.one(GeoLocation.class, (BigInteger) persistedAddress.get(0).get("_id"));
		final GeoLocation geoLocation = convertGoogleAddress(
				new ObjectMapper().readTree(google("geocode/json?latlng=" + latitude + ',' + longitude)));
		geoLocation.setLongitude(longitude);
		geoLocation.setLatitude(latitude);
		repository.save(geoLocation);
		return geoLocation;
	}

	public String map(String source, String destination, Contact contact) {
		String url;
		if (source == null || source.length() == 0)
			url = "https://maps.googleapis.com/maps/api/staticmap?{destination}&markers=icon:" + Strings.URL_APP
					+ "/images/mapMe.png|shadow:false|{destination}&scale=2&size=200x200&maptype=roadmap&key=";
		else {
			url = "https://maps.googleapis.com/maps/api/staticmap?{source}|{destination}&markers=icon:"
					+ Strings.URL_APP
					+ "/images/mapMe.png|shadow:false|{source}&markers=icon:" + Strings.URL_APP
					+ "/images/mapLoc.png|shadow:false|{destination}&scale=2&size=600x200&maptype=roadmap&sensor=true&key=";
			url = url.replaceAll("\\{source}", source);
		}
		url = url.replaceAll("\\{destination}", destination);
		url = url.replaceAll("\\{gender}", contact.getGender() == null ? "2" : "" + contact.getGender()) + googleKey;
		return Base64.getEncoder().encodeToString(
				WebClient.create(url).get().retrieve().toEntity(byte[].class).block().getBody());
	}

	private String round(float d) {
		final int digits = 5 + 1;
		String s = "" + d;
		if (s.length() - digits >= s.indexOf('.'))
			s = s.substring(0, s.indexOf('.') + digits);
		return s;
	}

	private void importLog(final String name, final String separator) throws Exception {
		final DateFormat dateParser = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);
		final Pattern pattern = Pattern.compile(
				"([\\d.]+) (\\S) (\\S) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(\\w+) ([^ ]*) ([^\"]*)\" (\\d+) (\\d+) \"([^\"]*)\" \"([^\"]*)\"");
		try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(name)))) {
			final QueryParams params = new QueryParams(Query.misc_listLog);
			String line;
			while ((line = reader.readLine()) != null) {
				Matcher m = pattern.matcher(line);
				if (m.find()) {
					final Log log = new Log();
					log.setIp(m.group(1));
					log.setMethod(m.group(5));
					log.setQuery(m.group(6));
					log.setStatus(Integer.parseInt(m.group(8)));
					if (!"-".equals(m.group(10))) {
						log.setReferer(m.group(10));
						if (log.getReferer().length() > 255)
							log.setReferer(log.getReferer().substring(0, 255));
					}
					final String date = Instant.ofEpochMilli(dateParser.parse(m.group(4)).getTime()).toString();
					log.setBody(date.substring(0, 19) + separator + m.group(11));
					if (log.getBody().length() > 255)
						log.setBody(log.getBody().substring(0, 255));
					log.setUri("ad");
					log.setPort(80);
					if ("/".equals(log.getQuery()) || log.getQuery().startsWith("/?")) {
						log.setQuery(log.getQuery().length() == 1 ? null : log.getQuery().substring(2));
						final String s[] = log.getBody().split(" \\| ");
						params.setSearch("log.createdAt='" + s[0] + "' and log.body='" + s[1]
								+ "' or log.body='" + log.getBody() + "'");
						if (repository.list(params).size() == 0)
							repository.save(log);
					}
				}
			}
		}
	}

	public void importLog() throws Exception {
		final String separator = " | ";
		importLog("log1", separator);
		importLog("log", separator);
		repository.executeUpdate("update Log set createdAt=substring_index(body,'" + separator
				+ "', 1), body=substring_index(body, '" + separator + "', -1) where uri='ad' and body like '%"
				+ separator + "%'");
		lookupIps();
	}

	private void lookupIps() throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listLog);
		params.setLimit(0);
		params.setSearch("log.uri='ad' and ip.org is null");
		final Result result = repository.list(params);
		final Pattern loc = Pattern.compile("\"loc\": \"([^\"]*)\"");
		for (int i = 0; i < result.size(); i++) {
			final String json = WebClient
					.create(lookupip.replace("{ip}", (String) result.get(i).get("log.ip"))).get()
					.retrieve().toEntity(String.class).block().getBody();
			final Ip ip = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
					.readValue(json, Ip.class);
			final Matcher m = loc.matcher(json);
			m.find();
			final String location = m.group(1);
			ip.setLatitude(Float.parseFloat(location.split(",")[0]));
			ip.setLongitude(Float.parseFloat(location.split(",")[1]));
			repository.save(ip);
		}
	}
}