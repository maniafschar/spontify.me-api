package com.jq.findapp.util;

import java.io.InputStream;
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;

@Component
public class Text {
	public enum TextId {
		date_weekday0,
		date_weekday1,
		date_weekday2,
		date_weekday3,
		date_weekday4,
		date_weekday5,
		date_weekday6,
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
		event_fbPoll,
		event_fbWithoutLocation,
		mail_contactPasswordReset,
		mail_contactRegistrationReminder,
		mail_contactWelcomeEmail,
		mail_title,
		mail_titleFrom,
		marketing_skySportCooperation,
		marketing_skySportPostfix,
		marketing_skySportSent,
		marketing_skySportSubjectPrefix,
		marketing_skySportText,
		marketing_skySportUnfinished,
		notification_authenticate,
		notification_chatNew,
		notification_chatSeen,
		notification_chatSentEntries,
		notification_chatSentEntry,
		notification_chatSentImg,
		notification_chatSentPos1,
		notification_chatSentPos2,
		notification_clientMarketing,
		notification_clientMarketingPoll,
		notification_clientMarketingPollPlayerOfTheMath,
		notification_clientMarketingPollPrediction,
		notification_clientMarketingPollResult,
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
		notification_eventParticipatePoll,
		notification_eventParticipateWithoutLocation,
		notification_eventRated,
		notification_feedback,
		notification_locationMarketing,
		or,
		today,
		tomorrow
	}

	@Autowired
	private Repository repository;

	private static ArrayNode categories;

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
							Json.toNode(IOUtils.toString(zip.getInputStream(entry), StandardCharsets.UTF_8)));
				}
			}
			categories = (ArrayNode) Json.toNode(
					IOUtils.toString(Text.class.getResourceAsStream("/json/categories.json"), StandardCharsets.UTF_8));
		} catch (final Exception e) {
			try (final InputStream in = Text.class.getResourceAsStream("/lang/DE.json")) {
				// Test environment, only german is tested
				languages.put("DE", Json.toNode(
						IOUtils.toString(in, StandardCharsets.UTF_8)));
			} catch (final Exception e1) {
				new RuntimeException(e);
			}
		}
	}

	public String getCategory(final String id) {
		final String[] s = id.split("\\.");
		for (int i = 0; i < categories.size(); i++) {
			if (s[0].equals(categories.get(i).get("key").asText())) {
				final ArrayNode values = (ArrayNode) categories.get(i).get("values");
				for (int i2 = 0; i2 < values.size(); i2++) {
					if (values.get(i2).asText().endsWith("|" + s[1]))
						return categories.get(i).get("label") + "." +
								values.get(i2).asText().replace("|" + s[1], "");
				}
			}
		}
		return id;
	}

	public String getText(final Contact contact, final TextId textId) {
		final String label = textId.name();
		String s;
		try {
			if (label.contains("_"))
				s = languages.get(contact.getLanguage()).get(label.substring(0, label.indexOf('_')))
						.get(label.substring(label.indexOf('_') + 1)).asText();
			else
				s = languages.get(contact.getLanguage()).get(label).asText();
			if (s.contains("{budd") || s.contains("APP_TITLE")) {
				final Client client = this.repository.one(Client.class, contact.getClientId());
				final JsonNode node = Json.toNode(Attachment.resolve(client.getStorage()));
				s = s.replaceAll("APP_TITLE", client.getName());
				s = s.replaceAll(" \\$\\{buddy}", node.get("lang").get(contact.getLanguage()).get("buddy").asText());
				s = s.replaceAll(" \\$\\{buddies}",
						node.get("lang").get(contact.getLanguage()).get("buddies").asText());
			}
			return s;
		} catch (final NullPointerException ex) {
			throw new RuntimeException("Missing label " + contact.getLanguage() + ": " + textId);
		} catch (final Exception ex) {
			throw new RuntimeException("Error on " + contact.getLanguage() + ": " + textId, ex);
		}
	}
}
