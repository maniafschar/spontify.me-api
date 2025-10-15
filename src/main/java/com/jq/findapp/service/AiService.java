package com.jq.findapp.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GoogleSearch;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import com.jq.wa2pdf.entity.Ticket;
import com.jq.wa2pdf.util.Utilities;
import com.vdurmont.emoji.EmojiParser;

@Service
public class AiService {
	@Value("${app.google.gemini.apiKey}")
	private String geminiKey;

	public AiSummary ask(final String text) {
		final List<Content> contents = ImmutableList.of(Content.builder().role("user")
				.parts(ImmutableList.of(Part.fromText(prompt + "\n" + text)))
				.build());
		final GenerateContentConfig config = GenerateContentConfig.builder()
				.thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
				.tools(Arrays.asList(Tool.builder().googleSearch(GoogleSearch.builder().build()).build())).build();
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
			return this.parseAdjectives(s.toString(), users);
		}
	}
}
