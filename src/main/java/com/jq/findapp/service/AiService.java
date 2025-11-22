package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.jq.findapp.entity.Ai;
import com.jq.findapp.entity.Ai.AiType;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.EventType;
import com.jq.findapp.entity.Event.Repetition;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

@Service
public class AiService {
	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	public static class Attributes {
		public String description;
		public List<String> values;
	}

	public Ai text(final String question) {
		final Result result = this.exists(question, AiType.Text, 365);
		if (result.size() > 0)
			return this.repository.one(Ai.class, (BigInteger) result.get(0).get("ai.id"));
		final String note = this.externalService.gemini(question, null);
		final Ai ai = new Ai();
		ai.setQuestion(question);
		ai.setNote(note);
		ai.setType(AiType.Text);
		this.repository.save(ai);
		return ai;
	}

	@Async
	public void lookup(final Contact contact) {
		final GeoLocation geoLocation = this.externalService.getAddress(contact.getLatitude(), contact.getLongitude(),
				false);
		if (geoLocation == null) {
			this.notificationService.createTicket(TicketType.ERROR, "AI",
					"GeoLocation not found: " + contact.getLatitude() + " / " + contact.getLongitude(), null);
			return;
		}
		if (Strings.isEmpty(contact.getSkills())) {
			this.locations("Please suggest interesting restaurants and clubs in and arround "
					+ geoLocation.getZipCode() + " " + geoLocation.getTown() + " " + geoLocation.getCountry());
			this.events("Please suggest interesting events in the next 30 days in and arround " + geoLocation.getTown()
					+ " " + geoLocation.getCountry(), contact);
		} else {
			Arrays.asList(contact.getSkills().split("\\|"))
					.stream().forEach(e -> {
						final String[] cat = this.text.getSkillText(e).split("\\.");
						if (cat != null) {
							this.locations("Please suggest interesting locations for " + cat[0] + ", especially "
									+ cat[1]
									+ ", in and arround " + geoLocation.getZipCode() + " " + geoLocation.getTown() + " "
									+ geoLocation.getCountry());
							this.events(
									"Please suggest interesting events in the next 30 days for " + cat[0]
											+ ", especially "
											+ cat[1] + ", in and arround " + geoLocation.getTown() + " "
											+ geoLocation.getCountry(),
									contact);
						}
					});
		}
	}

	public Attributes attributes(final String question) {
		final Result result = this.exists(question, AiType.Attibutes, 365);
		String answer;
		if (result.size() == 0)
			answer = this.externalService.gemini(question, this.createSchemaAttributes());
		else
			answer = result.get(0).get("ai.note").toString();
		return Json.toObject(answer, Attributes.class);
	}

	List<Location> locations(final String question) {
		Result result = this.exists(question, AiType.Location, 183);
		if (result.size() == 0) {
			this.importLocations(question,
					this.externalService.gemini(question, this.createSchemaLocationEvent(false)));
			result = this.exists(question, AiType.Location, 183);
		}
		final List<Location> locations = new ArrayList<>();
		if (result.size() > 0) {
			for (int i = 0; i < result.size(); i++)
				locations.add(
						this.repository.one(Location.class, new BigInteger(result.get(i).get("ai.note").toString())));
			return locations;
		}
		return locations;
	}

	private List<BigInteger> importLocations(final String question, final String answer) {
		final List<Location> locations = Arrays.asList(Json.toObject(answer, Location[].class));
		final ArrayNode nodes = (ArrayNode) Json.toNode(answer);
		final List<BigInteger> locationIds = new ArrayList<>();
		for (int i = 0; i < locations.size(); i++) {
			final Ai ai = new Ai();
			try {
				final Location location = locations.get(i);
				if (nodes.get(i).has("location_name"))
					location.setName(nodes.get(i).get("location_name").asText());
				location.setAddress(location.getStreet() + " " + location.getNumber() + "\n" + location.getZipCode()
						+ " " + location.getTown() + "\n" + location.getCountry());
				this.repository.save(location);
				ai.setNote(location.getId().toString());
				locationIds.add(location.getId());
			} catch (final IllegalArgumentException ex) {
				if (ex.getMessage().startsWith("exists:")) {
					final BigInteger id = new BigInteger(ex.getMessage().substring(ex.getMessage().indexOf(":") + 1));
					locationIds.add(id);
					ai.setNote(id.toString());
				} else
					locationIds.add(null);
			}
			if (!Strings.isEmpty(ai.getNote())) {
				ai.setQuestion(question);
				ai.setType(AiType.Location);
				this.repository.save(ai);
			}
		}
		return locationIds;
	}

	private List<Event> events(final String question, final Contact contact) {
		if (true)
			return null;
		final Result result = this.exists(question, AiType.Event, 7);
		if (result.size() == 0) {
			final String answer = this.externalService.gemini(question, this.createSchemaLocationEvent(true));
			final List<BigInteger> locationIds = this.importLocations(question, answer);
			final ArrayNode nodes = (ArrayNode) Json.toNode(answer);
			final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			for (int i = 0; i < nodes.size(); i++) {
				if (locationIds.get(i) != null) {
					final Event event = new Event();
					event.setContactId(this.repository.one(com.jq.findapp.entity.Client.class, contact.getClientId())
							.getAdminId());
					event.setDescription(nodes.get(i).get("description").asText());
					event.setType(EventType.Location);
					event.setRepetition(Repetition.Once);
					event.setLocationId(locationIds.get(i));
					try {
						event.setStartDate(new Timestamp(
								df.parse(nodes.get(i).get("date").asText().replace('T', ' ').replace('_', ' '))
										.getTime()));
						this.repository.save(event);
					} catch (final ParseException ex) {
						this.notificationService.createTicket(TicketType.ERROR, "AI event",
								Strings.stackTraceToString(ex),
								null);
					}
				}
			}
		}
		final List<Event> events = new ArrayList<>();
		for (int i = 0; i < result.size(); i++) {
			final Event event = this.repository.one(Event.class,
					new BigInteger(result.get(i).get("ai.note").toString()));
			if (event.getStartDate().after(new Timestamp(System.currentTimeMillis())))
				events.add(event);
		}
		return events;
	}

	private Result exists(final String qustion, final AiType type, final int daysBack) {
		final QueryParams params = new QueryParams(Query.misc_ai);
		params.setSearch("ai.question='" + qustion.replace("'", "''") + "' and ai.type='" + type.name() +
				"' and ai.createdAt>cast('" + Instant.now().minus(Duration.ofDays(daysBack)) + "' as timestamp)");
		return this.repository.list(params);
	}

	private Schema createSchemaLocationEvent(final boolean event) {
		final Map<String, Schema> location = new HashMap<>();
		location.put("country", Schema.builder().type(Type.Known.STRING).description("ISO 3166 Alpha 2 code").build());
		location.put("description", Schema.builder().type(Type.Known.STRING).build());
		location.put("email", Schema.builder().type(Type.Known.STRING).build());
		location.put("number", Schema.builder().type(Type.Known.STRING).build());
		location.put("street", Schema.builder().type(Type.Known.STRING).build());
		location.put("telephone", Schema.builder().type(Type.Known.STRING).build());
		location.put("town", Schema.builder().type(Type.Known.STRING).build());
		location.put("url", Schema.builder().type(Type.Known.STRING).build());
		location.put("zipCode", Schema.builder().type(Type.Known.STRING).build());
		final List<String> required = new ArrayList<>();
		if (event) {
			location.put("date", Schema.builder().type(Type.Known.STRING).format("date-time").build());
			location.put("location_name", Schema.builder().type(Type.Known.STRING).build());
			required.add("date");
			required.add("location_name");
		} else {
			location.put("name", Schema.builder().type(Type.Known.STRING).build());
			required.add("name");
		}
		required.add("description");
		required.add("street");
		required.add("number");
		required.add("zipCode");
		required.add("town");
		required.add("country");
		required.add("url");
		return Schema.builder()
				.type(Type.Known.ARRAY)
				.items(Schema.builder()
						.type(Type.Known.OBJECT)
						.properties(location)
						.required(required)
						.propertyOrdering(Arrays.asList("description", "street", "number", "zipCode",
								"town", "country", "url", "email", "telephone"))
						.build())
				.build();
	}

	private Schema createSchemaAttributes() {
		final Map<String, Schema> attributes = new HashMap<>();
		attributes.put("description", Schema.builder().type(Type.Known.STRING).build());
		attributes.put("values", Schema.builder().type(Type.Known.ARRAY).items(Schema.builder()
				.type(Type.Known.STRING).build()).build());
		return Schema.builder()
				.type(Type.Known.OBJECT)
				.properties(attributes)
				.required(Arrays.asList("description", "values"))
				.propertyOrdering(Arrays.asList("description", "values"))
				.build();
	}
}
