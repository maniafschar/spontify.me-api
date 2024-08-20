package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=";

	public SchedulerResult run() {
		final SchedulerResult result = new SchedulerResult();
		final Results results = new Results();
		try {
			final String zipCodePrefix = "" + (LocalDateTime.now().getDayOfYear() % 10);
			final JsonNode zip = new ObjectMapper().readTree(getClass().getResourceAsStream("/json/zip.json"));
			for (int i = 0; i < zip.size(); i++) {
				final String s = zip.get(i).get("zip").asText();
				if (s.startsWith("" + zipCodePrefix)) {
					final Results r = runZipCode(s);
					results.imported += r.imported;
					results.updated += r.updated;
					results.processed += r.processed;
					results.errors += r.errors;
					results.alreadyImported += r.alreadyImported;
					results.unchanged += r.unchanged;
				}
			}
			result.result = "prefix " + zipCodePrefix + "*" +
					"\nprocessed " + results.processed +
					"\nimported " + results.imported +
					"\nupdated " + results.updated +
					"\nunchanged " + results.unchanged +
					"\nalreadyImported " + results.alreadyImported +
					"\nerrors " + results.errors;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	Results runZipCode(String zip) throws Exception {
		final Results result = new Results();
		JsonNode list = new ObjectMapper().readTree(WebClient.create(URL + "search&zip=" + zip).get().retrieve()
				.toEntity(String.class).block().getBody());
		if (list.get("currentPageIndexEnd").intValue() > 0) {
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='importSportBars'");
			final Map<String, Object> storage = repository.one(params);
			@SuppressWarnings("unchecked")
			final Set<String> imported = new ObjectMapper()
					.readValue(storage.get("storage.storage").toString(), Set.class);
			for (int i = 0; i < list.get("numberOfPages").asInt(); i++) {
				if (i > 0) {
					try {
						list = new ObjectMapper()
								.readTree(WebClient.create(URL + "scroll&page=" + (i + 1) + "&zip=" + zip).get()
										.retrieve()
										.toEntity(String.class).block().getBody());
					} catch (Exception ex) {
						continue;
					}
				}
				for (int i2 = list.get("currentPageIndexStart").intValue() - 1; i2 < list.get("currentPageIndexEnd")
						.intValue(); i2++) {
					result.processed++;
					final JsonNode data = list.get("currentData").get("" + i2);
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
								location.historize();
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
			s.historize();
			s.setStorage(new ObjectMapper().writeValueAsString(imported));
			repository.save(s);
		}
		return result;
	}

	private boolean updateFields(final Location location, final JsonNode data) {
		boolean changed = false;
		if (!("|" + location.getSkills() + "|").contains("|x.1|")) {
			location.setSkills(Strings.isEmpty(location.getSkills()) ? "x.1" : location.getSkills() + "|x.1");
			changed = true;
		}
		if (Strings.isEmpty(location.getDescription()) && data.get("description").has("program")) {
			location.setDescription(data.get("description").get("program").asText());
			changed = true;
		}
		if (data.has("contactData")) {
			if (Strings.isEmpty(location.getTelephone()) && data.get("contactData").has("phoneNumber")) {
				location.setTelephone((data.get("contactData").has("phoneAreaCode")
						? data.get("contactData").get("phoneAreaCode").asText() + "/"
						: "") + data.get("contactData").get("phoneNumber").asText());
				changed = true;
			}
			if (Strings.isEmpty(location.getUrl()) && data.get("contactData").has("homepageUrl")) {
				location.setUrl(data.get("contactData").get("homepageUrl").asText());
				changed = true;
			}
			if (Strings.isEmpty(location.getEmail()) && data.get("contactData").has("mail")) {
				location.setEmail(data.get("contactData").get("mail").asText());
				changed = true;
			}
		}
		return changed;
	}

	class Results {
		int processed = 0;
		int imported = 0;
		int updated = 0;
		int errors = 0;
		int unchanged = 0;
		int alreadyImported = 0;
	}
}
