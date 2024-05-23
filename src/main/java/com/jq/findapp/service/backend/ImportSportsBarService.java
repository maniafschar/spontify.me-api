package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.LocalDateTime;

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
		if (now.getHour() < 10 && now.getMinute() < 9) {
			final Results results = new Results();
			try {
				final JsonNode zip = new ObjectMapper().readTree(getClass().getResourceAsStream("/json/zip.json"));
				final String prefix = "" + ((LocalDateTime.now().getDayOfMonth() + now.getHour()) % 10);
				for (int i = 0; i < zip.size(); i++) {
					final String s = zip.get(i).get("zip").asText();
					if (s.startsWith(prefix)) {
						final Results r = importZip(s);
						results.imported += r.imported;
						results.updated += r.updated;
						results.processed += r.processed;
						results.error += r.error;
					}
				}
				result.result = "prefix " + prefix + "*\n" + results.processed + " processed\n" + results.imported
						+ " imports\n" + results.updated + " updates\n" + results.error;
			} catch (final Exception e) {
				result.exception = e;
			}
		}
		return result;
	}

	public Results importZip(String zip) throws Exception {
		final Results result = new Results();
		final JsonNode list = new ObjectMapper()
				.readTree(WebClient.create(URL + zip).get().retrieve()
						.toEntity(String.class).block().getBody());
		for (int i2 = list.get("currentPageIndexStart").intValue() - 1; i2 < list.get("currentPageIndexEnd")
				.intValue(); i2++) {
			final JsonNode data = list.get("currentData").get("" + i2);
			if (data != null) {
				result.processed++;
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
					result.imported++;
				} catch (IllegalArgumentException ex) {
					if (ex.getMessage().startsWith("location exists: ")) {
						location = repository.one(Location.class, new BigInteger(ex.getMessage().substring(17)));
						location.historize();
						if (updateFields(location, data)) {
							repository.save(location);
							result.updated++;
						}
					} else {
						result.error++;
						if (!ex.getMessage().contains("OVER_QUERY_LIMIT"))
							notificationService.createTicket(TicketType.ERROR, "ImportSportsBar",
									Strings.stackTraceToString(ex), null);
					}
				} catch (Exception ex) {
					result.error++;
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
		int error = 0;
	}
}