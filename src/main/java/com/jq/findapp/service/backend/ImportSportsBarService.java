package com.jq.findapp.service.backend;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?";
	private static final String URL2 = "https://api.sportsbarfinder.net/map?rq=";

	public CronResult run() {
		final CronResult result = new CronResult();
		final Results results = new Results();
		try {
			final String zipCodePrefix = "" + (LocalDateTime.now().getDayOfYear() % 10);
			final JsonNode zip = Json
					.toNode(IOUtils.toString(getClass().getResourceAsStream("/json/zip.json"), StandardCharsets.UTF_8));
			for (int i = 0; i < zip.size(); i++) {
				final String s = zip.get(i).get("zip").asText();
				if (s.startsWith("" + zipCodePrefix)) {
					final Results r = zipCode(s);
					results.imported += r.imported;
					results.updated += r.updated;
					results.processed += r.processed;
					results.errors += r.errors;
					results.errorsScroll += r.errorsScroll;
					results.alreadyImported += r.alreadyImported;
					results.unchanged += r.unchanged;
				}
			}
			result.body = "prefix " + zipCodePrefix + "*" +
					"\nprocessed " + results.processed +
					"\nimported " + results.imported +
					"\nupdated " + results.updated +
					"\nunchanged " + results.unchanged +
					"\nalreadyImported " + results.alreadyImported +
					"\nerrors " + results.errors +
					"\nerrorsScroll " + results.errorsScroll;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public CronResult runFetch() {
		final CronResult result = new CronResult();
		final double longitudeMin = 47.27, longitudeMax = 54.92,
				latitudeMin = 5.87, latitudeMax = 15.03, delta = 0.02;
		final NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumFractionDigits(2);
		nf.setMaximumFractionDigits(2);
		int count = 0;
		for (double longitude = longitudeMin; longitude < longitudeMax; longitude += delta) {
			for (double latitude = latitudeMin; latitude < latitudeMax; latitude += delta) {
				try {
					final String s = IOUtils.toString(new URI(URL2 + URLEncoder.encode("{\"gd\":{},\"region\":{"
							+ "\"zoomLevel\":15"
							+ ",\"minLat\":" + longitude
							+ ",\"minLon\":" + latitude
							+ ",\"maxLat\":" + (longitude + delta)
							+ ",\"maxLon\":" + (latitude + delta)
							+ "},\"featureFilter\":[]}", StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
					if (s.contains("\"value\":[{\"")) {
						IOUtils.write(s,
								new FileOutputStream(
										"dazn/" + nf.format(longitude) + "-" + nf.format(latitude) + ".json"),
								StandardCharsets.UTF_8);
						count++;
					}
				} catch (Exception ex) {
					if (!"Not enough variable values available to expand '\"region\"'".equals(ex.getMessage()))
						result.exception = ex;
				}
			}
		}
		result.body = count + " imports";
		return result;
	}

	public CronResult runImport() {
		final CronResult result = new CronResult();
		int count = 0;
		final List<String> files = Arrays.asList(new File("dazn").list());
		files.sort((a, b) -> a.compareTo(b));
		for (String file : files) {
			if (file.endsWith(".json")) {
				try {
					final JsonNode n = Json.toNode(IOUtils.toString(new FileInputStream(new File("dazn/" + file)),
							StandardCharsets.UTF_8));
					for (int i = 0; i < n.get("value").size(); i++) {
						final JsonNode l = n.get("value").get(i);
						final Location location = new Location();
						location.setName(l.get("name").asText());
						location.setLatitude((float) l.get("address").get("latitude").asDouble());
						location.setLongitude((float) l.get("address").get("longitude").asDouble());
						location.setStreet(l.get("address").get("street").asText());
						if (location.getStreet().contains(" ")) {
							location.setNumber(
									location.getStreet().substring(location.getStreet().lastIndexOf(" ") + 1));
							location.setStreet(
									location.getStreet().substring(0, location.getStreet().lastIndexOf(" ")));
						}
						location.setZipCode(l.get("address").get("zip").asText());
						location.setTown(l.get("address").get("city").asText());
						location.setCountry(l.get("address").get("countryCode").asText());
						location.setSkills("x.1");
						location.setAddress(
								location.getStreet()
										+ (Strings.isEmail(location.getNumber()) ? "" : " " + location.getNumber())
										+ "\n" + location.getZipCode() + " " + location.getTown() + "\nDeutschland");
						try {
							repository.save(location);
							count++;
						} catch (IllegalArgumentException ex) {
							if (ex.getMessage().startsWith("location exists: ")) {
								final Location loc = repository.one(Location.class,
										new BigInteger(ex.getMessage().substring(17)));
								if (Strings.isEmpty(loc.getSkills()) || !loc.getSkills().contains("x.1")) {
									loc.setSkills(Strings.isEmpty(loc.getSkills()) ? "x.1" : loc.getSkills() + "|x.1");
									repository.save(loc);
								}
							}
						}
					}
					new File("dazn/" + file).delete();
				} catch (Exception ex) {
					result.exception = ex;
				}
				if (result.exception != null || count > 1)
					break;
			}
		}
		result.body = count + " imports";
		return result;
	}

	Results zipCode(String zip) throws Exception {
		final Results result = new Results();
		final MultiValueMap<String, String> cookies = new LinkedMultiValueMap<>();
		JsonNode list = Json.toNode(WebClient
				.create(URL + "detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=search&zip="
						+ zip)
				.get()
				.accept(MediaType.APPLICATION_JSON)
				.exchangeToMono(response -> {
					if (response.statusCode().is2xxSuccessful()) {
						response.cookies()
								.forEach((key, respCookies) -> cookies.add(key, respCookies.get(0).getValue()));
						return response.bodyToMono(String.class);
					}
					return response.createError();
				}).block());
		if (list.get("currentPageIndexEnd").intValue() > 0) {
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='importSportBars'");
			final Map<String, Object> storage = repository.one(params);
			@SuppressWarnings("unchecked")
			final Set<String> imported = Json.toObject(storage.get("storage.storage").toString(), Set.class);
			for (int i = 0; i < list.get("numberOfPages").asInt(); i++) {
				if (i > 0) {
					try {
						list = Json.toNode(WebClient.create(URL + "action=scroll&page=" + (i + 1))
								.get()
								.cookies(cookieMap -> cookieMap.addAll(cookies))
								.retrieve()
								.toEntity(String.class).block().getBody());

					} catch (Exception ex) {
						result.errorsScroll++;
						notificationService.createTicket(TicketType.ERROR, "ImportSportsBarService.scroll",
								Strings.stackTraceToString(ex), null);
						continue;
					}
				}
				for (int i2 = 0; i2 < list.get("currentPageIndexEnd").intValue()
						- list.get("currentPageIndexStart").intValue() + 1; i2++) {
					final JsonNode data = list.get("currentData").get("" + i2);
					result.processed++;
					if (imported.contains(data.get("number").asText()))
						result.alreadyImported++;
					else {
						final String street = data.get("description").get("street").asText();
						Location location = new Location();
						location.setName(data.get("name").asText());
						if (street.contains(" ") && street.substring(street.lastIndexOf(' ')).trim().matches("\\d.*")) {
							location.setStreet(street.substring(0, street.lastIndexOf(' ')));
							location.setNumber(street.substring(street.lastIndexOf(' ')).trim());
						} else
							location.setStreet(street);
						location.setZipCode(zip);
						location.setTown(data.get("description").get("city").asText().replace(zip, "").trim());
						location.setAddress(
								location.getStreet() + " " + location.getNumber() + "\n" + location.getZipCode()
										+ " " + location.getTown() + "\nDeutschland");
						location.setCountry("DE");
						if (data.has("mapdata") && data.get("mapdata").has("latitude")) {
							location.setLatitude(data.get("mapdata").get("latitude").floatValue());
							location.setLongitude(data.get("mapdata").get("longitude").floatValue());
						}
						updateFields(location, data);
						location.setContactId(BigInteger.ONE);
						try {
							repository.save(location);
							imported.add(data.get("number").asText());
							result.imported++;
						} catch (IllegalArgumentException ex) {
							if (ex.getMessage().startsWith("location exists: ")) {
								location = repository.one(Location.class,
										new BigInteger(ex.getMessage().substring(17)));
								if (updateFields(location, data)) {
									repository.save(location);
									result.updated++;
								} else
									result.unchanged++;
								imported.add(data.get("number").asText());
							} else {
								result.errors++;
								if (!ex.getMessage().contains("OVER_QUERY_LIMIT"))
									notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
											Strings.stackTraceToString(ex), null);
							}
						} catch (Exception ex) {
							result.errors++;
							notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
									Strings.stackTraceToString(ex), null);
						}
					}
				}
			}
			final Storage s = repository.one(Storage.class, (BigInteger) storage.get("storage.id"));
			s.setStorage(Json.toString(imported));
			repository.save(s);
		}
		return result;
	}

	private boolean updateFields(final Location location, final JsonNode data) {
		if (!("|" + location.getSkills() + "|").contains("|x.1|"))
			location.setSkills(Strings.isEmpty(location.getSkills()) ? "x.1" : location.getSkills() + "|x.1");
		if (Strings.isEmpty(location.getDescription()) && data.get("description").has("program"))
			location.setDescription(data.get("description").get("program").asText());
		if (data.has("contactData")) {
			if (Strings.isEmpty(location.getTelephone()) && data.get("contactData").has("phoneNumber"))
				location.setTelephone((data.get("contactData").has("phoneAreaCode")
						? data.get("contactData").get("phoneAreaCode").asText() + "/"
						: "") + data.get("contactData").get("phoneNumber").asText());
			if (Strings.isEmpty(location.getUrl()) && data.get("contactData").has("homepageUrl"))
				location.setUrl(data.get("contactData").get("homepageUrl").asText());
			if (Strings.isEmpty(location.getEmail()) && data.get("contactData").has("mail"))
				location.setEmail(data.get("contactData").get("mail").asText());
		}
		return location.modified();
	}

	class Results {
		int processed = 0;
		int imported = 0;
		int updated = 0;
		int errors = 0;
		int errorsScroll = 0;
		int unchanged = 0;
		int alreadyImported = 0;
	}
}