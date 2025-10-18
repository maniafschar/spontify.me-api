package com.jq.findapp.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
import com.jq.findapp.entity.Location;
import com.jq.findapp.util.Json;

@Service
public class AiService {
	@Value("${app.google.gemini.apiKey}")
	private String geminiKey;

	public List<Location> locations(final String question) {
		final List<Content> contents = ImmutableList.of(Content.builder().role("user")
				.parts(ImmutableList.of(Part.fromText(question)))
				.build());
		final GenerateContentConfig config = GenerateContentConfig.builder()
				.thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
				.responseMimeType("application/json")
				.responseSchema(this.createLocationSchema())
				.build();
		try (final ResponseStream<GenerateContentResponse> responseStream = Client.builder().apiKey(this.geminiKey)
				.build().models.generateContentStream("gemini-2.5-flash-lite", contents, config)) {
			final StringBuffer s = new StringBuffer();
			for (final GenerateContentResponse res : responseStream) {
				if (res.candidates().isEmpty() || res.candidates().get().get(0).content().isEmpty()
						|| res.candidates().get().get(0).content().get().parts().isEmpty())
					continue;
				final List<Part> parts = res.candidates().get().get(0).content().get().parts().get();
				for (final Part part : parts)
					s.append(part.text().orElse(""));
			}
			return Arrays.asList(Json.toObject(s.toString(), Location[].class));
		}
	}

	private Schema createLocationSchema() {
		final Map<String, Schema> location = new HashMap<>();
		location.put("country", Schema.builder().type(Type.Known.STRING).description("ISO 3166 Alpha 2 code").build());
		location.put("description", Schema.builder().type(Type.Known.STRING).build());
		location.put("email", Schema.builder().type(Type.Known.STRING).build());
		location.put("name", Schema.builder().type(Type.Known.STRING).build());
		location.put("number", Schema.builder().type(Type.Known.STRING).build());
		location.put("street", Schema.builder().type(Type.Known.STRING).build());
		location.put("telephone", Schema.builder().type(Type.Known.STRING).build());
		location.put("town", Schema.builder().type(Type.Known.STRING).build());
		location.put("url", Schema.builder().type(Type.Known.STRING).build());
		location.put("zipCode", Schema.builder().type(Type.Known.STRING).build());

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