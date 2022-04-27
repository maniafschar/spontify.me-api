package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.io.IOUtils;

public enum Text {
	mailAccountDelete,
	mailBirthday,
	mailChatLocation,
	mailFeedback,
	mailFeedbackResponse,
	mailFindMe,
	mailFriendAppro,
	mailFriendReq,
	mailInvite,
	mailLocMarketing,
	mailMarkEvent,
	mailMGMarketing,
	mailNewMsg,
	mailPWReset,
	mailSentImg,
	mailSentPos1,
	mailSentPos2,
	mailSentEntries,
	mailSentEntry,
	mailRatingLocMat,
	mailRatingProfile,
	mailVisitLocation,
	mailVisitProfile,
	mailWelcomeExt,
	mailWelcome,
	mailWTD;

	private static Map<String, JsonNode> languages = new HashMap<>();

	static {
		final String src = Text.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		try (ZipFile zip = new ZipFile(src.substring(src.indexOf(':') + 1, src.indexOf('!')))) {
			final Enumeration<? extends ZipEntry> e = zip.entries();
			while (e.hasMoreElements()) {
				final ZipEntry entry = e.nextElement();
				if (entry.getName().contains("/lang/") && entry.getName().endsWith(".json")) {
					languages.put(
							entry.getName().substring(entry.getName().lastIndexOf("/") + 1,
									entry.getName().lastIndexOf(".")),
							new ObjectMapper()
									.readTree(IOUtils.toString(zip.getInputStream(entry), StandardCharsets.UTF_8)));
				}
			}
		} catch (Exception e) {
			try {
				// Test environment, only german is tested
				languages.put("DE", new ObjectMapper().readTree(
						IOUtils.toString(Text.class.getResourceAsStream("/lang/DE.json"), StandardCharsets.UTF_8)));
			} catch (Exception e1) {
				new RuntimeException(e);
			}
		}
	}

	public String getText(String language) {
		return languages.get(language).get(name()).asText();
	}
}
