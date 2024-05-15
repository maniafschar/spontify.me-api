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

	public SchedulerResult importSportsBars() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/importSportsBars");
		final LocalDateTime now = LocalDateTime.now();
		if (now.getHour() == 4 && now.getMinute() < 10) {
			try {
				final JsonNode zip = new ObjectMapper().readTree(getClass().getResourceAsStream("/json/zip.json"));
				for (int i = 0; i < zip.size(); i++) {
					final String s = zip.get(i).get("zip").asText();
					if (s.startsWith("" + (LocalDateTime.now().getDayOfMonth() % 10)))
						importZip(s);
				}
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
		int count = 0;
		for (int i2 = list.get("currentPageIndexStart").intValue() - 1; i2 < list.get("currentPageIndexEnd")
				.intValue(); i2++) {
			final JsonNode data = list.get("currentData").get("" + i2);
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
				if (data.get("contactData").has("phoneNumber"))
					location.setTelephone(data.get("contactData").get("phoneAreaCode").asText() + "/" +
							data.get("contactData").get("phoneNumber").asText());
				if (data.get("contactData").has("homepageUrl"))
					location.setUrl(data.get("contactData").get("homepageUrl").asText());
				if (data.get("contactData").has("mail"))
					location.setUrl(data.get("contactData").get("mail").asText());
				location.setSkills("x.1");
				location.setContactId(BigInteger.ONE);
				repository.save(location);
				count++;
			} else {
				location = repository.one(Location.class, (BigInteger) result.get(0).get("location.id"));
				if (!("|" + location.getSkills() + "|").contains("|x.1|")) {
					location.setSkills(Strings.isEmpty(location.getSkills()) ? "x.1" : location.getSkills() + "|x.1");
					repository.save(location);
					count++;
				}
			}
			System.out.println(data.get("name").asText());
		}
		return count;
	}
}
