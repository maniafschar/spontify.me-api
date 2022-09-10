package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum Text {
	engagement_addFriends,
	engagement_becomeGuide,
	engagement_bluetoothMatch,
	engagement_bluetoothNoMatch,
	engagement_guide,
	engagement_installCurrentVersion,
	engagement_like,
	engagement_nearByContact,
	engagement_nearByEvent,
	engagement_nearByLocation,
	engagement_newTown,
	engagement_patience,
	engagement_uploadProfileAttributes,
	engagement_uploadProfileImage,
	mail_accountDelete,
	mail_birthday,
	mail_chatLocation,
	mail_chatSeen,
	mail_feedback,
	mail_feedbackResponse,
	mail_findMe,
	mail_friendAppro,
	mail_friendReq,
	mail_invite,
	mail_locMarketing,
	mail_markEvent,
	mail_newsTitle,
	mail_newsTitleFrom,
	mail_pwReset,
	mail_sentImg,
	mail_sentPos1,
	mail_sentPos2,
	mail_sentEntries,
	mail_sentEntry,
	mail_ratingLocMat,
	mail_ratingProfile,
	mail_registrationReminder,
	mail_visitLocation,
	mail_visitProfile,
	mail_welcome,
	mail_welcomeExt,
	mail_wtd,
	marketing_iPadText,
	marketing_iPadTitle,
	marketing_list,
	marketing_noActions,
	marketing_scoring;

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
						IOUtils.toString(Text.class.getResourceAsStream("/lang/EN.json"), StandardCharsets.UTF_8)));
			} catch (Exception e1) {
				new RuntimeException(e);
			}
		}
	}

	public String getText(String language) {
		return languages.get(language).get(name().substring(0, name().indexOf('_')))
				.get(name().substring(name().indexOf('_') + 1)).asText();
	}
}
