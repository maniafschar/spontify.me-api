package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.LocalDateTime;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;

@Service
public class ImportSportsBarService {
	@Autowired
	private Repository repository;

	private static final String URL = "https://skyfinder.sky.de/sf/skyfinder.servlet?detailedSearch=Suchen&group=H&group=B&group=A&country=de&action=search&zip=";

	private int imported = 0, updated = 0;

	public SchedulerResult importSportsBars() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importSportsBars");
		final LocalDateTime now = LocalDateTime.now();
		if (now.getHour() == 4 && now.getMinute() < 9) {
			try {
				final JsonNode zip = new ObjectMapper().readTree(getClass().getResourceAsStream("/json/zip.json"));
				final String prefix = "" + (LocalDateTime.now().getDayOfMonth() % 10);
				for (int i = 0; i < zip.size(); i++) {
					final String s = zip.get(i).get("zip").asText();
					if (s.startsWith(prefix))
						importZip(s);
				}
				result.result = imported + "imports/" + updated + " updates on " + prefix + "*";
			} catch (final Exception e) {
				result.exception = e;
			}
		}
		return result;
	}

	int importZip(String zip) throws Exception {
		final QueryParams params = new QueryParams(Query.location_listId);
		final JsonNode list = new ObjectMapper()
				.readTree(WebClient.create(URL + zip).get().retrieve()
						.toEntity(String.class).block().getBody());
		for (int i2 = list.get("currentPageIndexStart").intValue() - 1; i2 < list.get("currentPageIndexEnd")
				.intValue(); i2++) {
			final JsonNode data = list.get("currentData").get("" + i2);
			if (data != null) {
				String street = data.get("description").get("street").asText();
				params.setSearch("location.country='DE' and location.country='" + zip + "' and (location.name like '"
						+ data.get("name").asText().replace('\'', '_') + "' or location.street like '"
						+ street.substring(0, street.lastIndexOf(' ')).replace('\'', '_') + "%')");
				final Result result = repository.list(params);
				final Location location;
				if (result.size() == 0) {
					location = new Location();
					location.setName(data.get("name").asText());
					location.setStreet(street.substring(0, street.lastIndexOf(' ')));
					location.setNumber(street.substring(street.lastIndexOf(' ')).trim());
					location.setZipCode(zip);
					location.setTown(data.get("description").get("city").asText().replace(zip, "").trim());
					location.setAddress(location.getStreet() + " " + location.getNumber() + "\n" + location.getZipCode()
							+ " " + location.getTown() + "\nDeutschland");
					location.setCountry("DE");
					updateFields(location, data);
					location.setContactId(BigInteger.ONE);
					repository.save(location);
					imported++;
				} else {
					location = repository.one(Location.class, (BigInteger) result.get(0).get("location.id"));
					if (updateFields(location, data)) {
						repository.save(location);
						updated++;
					}
				}
			}
		}
		return imported + updated;
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
			location.setTelephone(data.get("contactData").get("phoneAreaCode").asText() + "/"
					+ data.get("contactData").get("phoneNumber").asText());
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
