package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Type;
import com.jq.findapp.entity.Ai;
import com.jq.findapp.entity.Ai.AiType;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class AiService {
	@Value("${app.google.gemini.apiKey}")
	private String geminiKey;

	@Autowired
	private Repository repository;

	public Ai text(final String question) {
		final Result result = this.exists(question, AiType.Text, 365);
		if (result.size() > 0)
			return this.repository.one(Ai.class, (BigInteger) result.get(0).get("ai.id"));
		final String answer = this.call(question, null);
		final Ai ai = new Ai();
		ai.setQuestion(question);
		ai.setAnswer(answer);
		ai.setType(AiType.Text);
		this.repository.save(ai);
		return ai;
	}

	public List<Location> locations(final String question) {
		final Result result = this.exists(question, AiType.Location, 183);
		if (result.size() > 0) {
			final List<Location> locations = new ArrayList<>();
			for (int i = 0; i < result.size(); i++)
				locations.add(
						this.repository.one(Location.class, new BigInteger(result.get(i).get("ai.answer").toString())));
			return locations;
		}
		final List<Location> locations = Arrays.asList(Json.toObject(
				this.call(question, this.createSchemaLocation(false)), Location[].class));
		for (final Location location : locations) {
			final Ai ai = new Ai();
			try {
				this.repository.save(location);
				ai.setAnswer(location.getId().toString());
			} catch (final IllegalArgumentException ex) {
				if (ex.getMessage().startsWith("location exists: "))
					ai.setAnswer(new BigInteger(ex.getMessage().substring(17)).toString());
			}
			if (!Strings.isEmpty(ai.getAnswer())) {
				ai.setQuestion(question);
				ai.setType(AiType.Location);
				this.repository.save(ai);
			}
		}
		return locations;
	}

	public List<Event> events(final String question) {
		final Result result = this.exists(question, AiType.Event, 7);
		if (result.size() > 0) {
			final List<Event> events = new ArrayList<>();
			for (int i = 0; i < result.size(); i++) {
				final Event event = this.repository.one(Event.class,
						new BigInteger(result.get(i).get("ai.answer").toString()));
				if (event.getStartDate().after(new Timestamp(System.currentTimeMillis())))
					events.add(event);
			}
			if (events.size() > 0)
				return events;
		}
		final ArrayNode eventNodes = (ArrayNode) Json.toNode(this.call(question, this.createSchemaLocation(true)));
		final List<Event> events = new ArrayList<>();
		for (final JsonNode eventNode : eventNodes) {
			final Ai ai = new Ai();
			try {
				final Location location = new Location();
				this.repository.save(location);
				ai.setAnswer(location.getId().toString());
			} catch (final IllegalArgumentException ex) {
				if (ex.getMessage().startsWith("location exists: "))
					ai.setAnswer(new BigInteger(ex.getMessage().substring(17)).toString());
			}
			if (!Strings.isEmpty(ai.getAnswer())) {
				ai.setQuestion(question);
				ai.setType(AiType.Location);
				this.repository.save(ai);
			}
		}
		return events;
	}

	private Result exists(final String qustion, final AiType type, final int daysBack) {
		final QueryParams params = new QueryParams(Query.misc_ai);
		params.setSearch("ai.question='" + qustion + "' and ai.type='" + type.name() +
				"' and ai.createdAt>cast('" + Instant.now().minus(Duration.ofDays(daysBack)) + "' as timestamp)");
		return this.repository.list(params);
	}

	private String call(final String question, final Schema schema) {
		final GenerateContentConfig.Builder config = GenerateContentConfig.builder()
				.thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build());
		if (schema != null)
			config.responseMimeType("application/json").responseSchema(schema);
		final List<Content> contents = ImmutableList.of(Content.builder().role("user")
				.parts(ImmutableList.of(Part.fromText(question))).build());
		try (final ResponseStream<GenerateContentResponse> responseStream = Client.builder().apiKey(this.geminiKey)
				.build().models.generateContentStream("gemini-2.5-flash-lite", contents, config.build())) {
			final StringBuffer s = new StringBuffer();
			for (final GenerateContentResponse res : responseStream) {
				if (res.candidates().isEmpty() || res.candidates().get().get(0).content().isEmpty()
						|| res.candidates().get().get(0).content().get().parts().isEmpty())
					continue;
				final List<Part> parts = res.candidates().get().get(0).content().get().parts().get();
				for (final Part part : parts)
					s.append(part.text().orElse(""));
			}
			return s.toString();
		}
	}

	private Schema createSchemaLocation(final boolean event) {
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
		if (event) {
			location.put("date", Schema.builder().type(Type.Known.STRING).format("date-time").build());
			location.put("location_name", Schema.builder().type(Type.Known.STRING).build());
		} else
			location.put("name", Schema.builder().type(Type.Known.STRING).build());
		return Schema.builder()
				.type(Type.Known.ARRAY)
				.items(Schema.builder()
						.type(Type.Known.OBJECT)
						.properties(location)
						.required(Arrays.asList("name", "description", "street", "number", "zipCode", "town",
								"country", "url"))
						.propertyOrdering(Arrays.asList("name", "description", "street", "number", "zipCode",
								"town", "country", "url", "email", "telephone", "url"))
						.build())
				.build();
	}
}