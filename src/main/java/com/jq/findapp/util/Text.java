package com.jq.findapp.util;

import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Repository;

@Component
public class Text {
	public enum TextId {
		category_verb0,
		category_verb1,
		category_verb2,
		category_verb3,
		category_verb4,
		category_verb5,
		engagement_addEvent,
		engagement_addFriends,
		engagement_ai,
		engagement_allowLocation,
		engagement_bluetoothMatch,
		engagement_bluetoothNoMatch,
		engagement_installCurrentVersion,
		engagement_like,
		engagement_nearByContact,
		engagement_nearByLocation,
		engagement_newContacts,
		engagement_newLocations,
		engagement_newTown,
		engagement_patience,
		engagement_praise,
		engagement_uploadProfileAttributes,
		engagement_uploadProfileImage,
		engagement_welcome,
		mail_contactPasswordReset,
		mail_contactRegistrationReminder,
		mail_contactWelcomeEmail,
		mail_title,
		mail_titleFrom,
		notification_authenticate,
		notification_chatNew,
		notification_chatSeen,
		notification_clientMarketing,
		notification_clientMarketingResult,
		notification_clientNews,
		notification_contactBirthday,
		notification_contactDelete,
		notification_contactFindMe,
		notification_contactFriendApproved,
		notification_contactFriendRequest,
		notification_contactVideoCall,
		notification_contactVisitLocation,
		notification_contactVisitProfile,
		notification_eventChanged,
		notification_eventDelete,
		notification_eventNotify,
		notification_eventNotifyWithoutLocation,
		notification_eventNotification,
		notification_eventParticipate,
		notification_eventParticipateOnline,
		notification_eventParticipateWithoutLocation,
		notification_eventRated,
		notification_feedback,
		notification_locationMarketing,
		notification_sentImg,
		notification_sentPos1,
		notification_sentPos2,
		notification_sentEntries,
		notification_sentEntry,
		or,
		preventDelete,
		today,
		tomorrow
	}

	@Autowired
	private Repository repository;

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
		} catch (final Exception e) {
			try {
				// Test environment, only german is tested
				languages.put("DE", new ObjectMapper().readTree(
						IOUtils.toString(Text.class.getResourceAsStream("/lang/DE.json"), StandardCharsets.UTF_8)));
			} catch (final Exception e1) {
				new RuntimeException(e);
			}
		}
	}

	public String getText(final Contact contact, final TextId textId) {
		final String label = textId.name();
		String s;
		if (label.contains("_"))
			s = languages.get(contact.getLanguage()).get(label.substring(0, label.indexOf('_')))
					.get(label.substring(label.indexOf('_') + 1)).asText();
		else
			s = languages.get(contact.getLanguage()).get(label).asText();
		if (s.contains("APP_")) {
			final Client client = repository.one(Client.class, contact.getClientId());
			s = s.replaceAll("APP_TITLE", client.getName());
		}
		return s;
	}
}