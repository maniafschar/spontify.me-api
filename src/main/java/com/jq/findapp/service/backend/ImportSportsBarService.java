package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=search&zip=";

	public SchedulerResult importSportsBars() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importSportsBars");
		final LocalDateTime now = LocalDateTime.now();
		if (now.getHour() == 4 || now.getHour() == 5 && now.getMinute() < 39) {
			int imported = 0, updated = 0, processed = 0, error = 0;
			try {
				imported = 0;
				updated = 0;
				final JsonNode zip = new ObjectMapper().readTree(getClass().getResourceAsStream("/json/zip.json"));
				final String prefix = "" + (now.getHour() == 4 ? 0 : 6) + now.getMinute() / 10;
				for (int i = 0; i < zip.size(); i++) {
					final String s = zip.get(i).get("zip").asText();
					if (s.startsWith(prefix)) {
						final Map<String, Integer> r = importZip(s);
						imported += r.get("imported");
						updated += r.get("updated");
						processed += r.get("processed");
						error += r.get("error");
					}
				}
				result.result = processed + " processed/" + imported + " imports/" + updated + " updates/" + error
						+ " errors on " + prefix + "*";
			} catch (final Exception e) {
				result.exception = e;
			}
		}
		return result;
	}

	public Map<String, Integer> importZip(String zip) throws Exception {
		final Map<String, Integer> result = new HashMap<>();
		result.put("processed", 0);
		result.put("imported", 0);
		result.put("updated", 0);
		result.put("error", 0);
		final JsonNode list = new ObjectMapper()
				.readTree(WebClient.create(URL + zip).get().retrieve()
						.toEntity(String.class).block().getBody());
		for (int i2 = list.get("currentPageIndexStart").intValue() - 1; i2 < list.get("currentPageIndexEnd")
				.intValue(); i2++) {
			final JsonNode data = list.get("currentData").get("" + i2);
			if (data != null) {
				result.put("processed", result.get("processed") + 1);
				final String street = data.get("description").get("street").asText();
				Location location = new Location();
				location.setName(data.get("name").asText());
				if (street.contains(" ")) {
					location.setStreet(street.substring(0, street.lastIndexOf(' ')));
					location.setNumber(street.substring(street.lastIndexOf(' ')).trim());
				} else
					location.setStreet(street);
				location.setZipCode(zip);
				location.setTown(data.get("description").get("city").asText().replace(zip, "").trim());
				location.setAddress(location.getStreet() + " " + location.getNumber() + "\n" + location.getZipCode()
						+ " " + location.getTown() + "\nDeutschland");
				location.setCountry("DE");
				updateFields(location, data);
				location.setContactId(BigInteger.ONE);
				try {
					repository.save(location);
					result.put("imported", result.get("imported") + 1);
				} catch (IllegalArgumentException ex) {
					if (ex.getMessage().startsWith("location exists: ")) {
						location = repository.one(Location.class, new BigInteger(ex.getMessage().substring(17)));
						if (updateFields(location, data)) {
							repository.save(location);
							result.put("updated", result.get("updated") + 1);
						}
					} else {
						result.put("error", result.get("error") + 1);
						if (!ex.getMessage().contains("OVER_QUERY_LIMIT"))
							notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
									Strings.stackTraceToString(ex), null);
					}
				} catch (Exception ex) {
					result.put("error", result.get("error") + 1);
					notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
							Strings.stackTraceToString(ex), null);
				}
			}
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
		return changed;
	}
}
