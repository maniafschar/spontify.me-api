package com.jq.findapp.service;

import java.math.BigInteger;
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
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Json;

@Service
public class AiService {
	@Value("${app.google.gemini.apiKey}")
	private String geminiKey;

	@Autowired
	private Repository repository;

	public String text(final String question) {
		final String answer = this.call(question, null);
		final Ai ai = new Ai();
		ai.setQuestion(question);
		ai.setAnswer(answer);
		ai.setType(AiType.Text);
		this.repository.save(ai);
		return answer;
	}

	public List<Location> locations(final String question) {
		final List<Location> locations = Arrays.asList(Json.toObject(
				this.call(question, this.createSchema(false)), Location[].class));
		for (Location location : locations) {
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
		final ArrayNode events = (ArrayNode) Json.toNode(this.call(question, this.createSchema(true)));
		for (Node event : events) {
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

	private String call(final String question, final Schema schema) {
		final GenerateContentConfig.Builder config = GenerateContentConfig.builder()
				.thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build());
		if (schema != null)
			config.responseMimeType("application/json").responseSchema(schema);
		final List<Content> contents = ImmutableList.of(Content.builder().role("user")
				.parts(ImmutableList.of(Part.fromText(question)))
				.build());
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

	private Schema createSchema(boolean event) {
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
