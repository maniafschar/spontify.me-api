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
	category_verb0,
	category_verb1,
	category_verb2,
	category_verb3,
	category_verb4,
	category_verb5,
	engagement_addEvent,
	engagement_addFriends,
	engagement_allowLocation,
	engagement_becomeGuide,
	engagement_bluetoothMatch,
	engagement_bluetoothNoMatch,
	engagement_guide,
	engagement_installCurrentVersion,
	engagement_like,
	engagement_nearByContact,
	engagement_nearByLocation,
	engagement_newTown,
	engagement_patience,
	engagement_praise,
	engagement_uploadProfileAttributes,
	engagement_uploadProfileImage,
	mail_chatLocation,
	mail_chatNew,
	mail_chatSeen,
	mail_contactBirthday,
	mail_contactDelete,
	mail_contactFindMe,
	mail_contactFriendApproved,
	mail_contactFriendRequest,
	mail_contactPasswordReset,
	mail_contactRegistrationReminder,
	mail_contactVisitLocation,
	mail_contactVisitProfile,
	mail_contactWelcomeEmail,
	mail_contactWhatToDo,
	mail_eventChanged,
	mail_eventDelete,
	mail_eventNotify,
	mail_eventNotification,
	mail_eventParticipate,
	mail_feedback,
	mail_locationMarketing,
	mail_locationRatingMatch,
	mail_newsTitle,
	mail_newsTitleFrom,
	mail_sentImg,
	mail_sentPos1,
	mail_sentPos2,
	mail_sentEntries,
	mail_sentEntry,
	mail_welcome,
	marketing_event,
	marketing_eventCheckedIn,
	marketing_eventCheckedInFailed,
	marketing_eventCheckedOut,
	marketing_eventCheckedOutFailed,
	marketing_iPadText,
	marketing_iPadTitle,
	marketing_list,
	marketing_noActions,
	marketing_scoring,
	or,
	preventDelete;

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
		if (name().contains("_"))
			return languages.get(language).get(name().substring(0, name().indexOf('_')))
					.get(name().substring(name().indexOf('_') + 1)).asText();
		return languages.get(language).get(name()).asText();
	}
}
