package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Text;

@Service
public class EngagementService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthenticationService authenticationService;

	private static ExternalService externalService;

	private static String currentVersion;

	@Autowired
	private void setExternalService(ExternalService externalService) {
		EngagementService.externalService = externalService;
	}

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static class ChatTemplate {
		private final Text textId;
		private final String action;
		private final Condition condition;

		interface Condition {
			boolean isTrue(Contact contact);
		}

		private ChatTemplate(Text textId, String action, Condition condition) {
			this.textId = textId;
			this.condition = condition;
			this.action = action;
		}

		boolean eligible(Contact contact) {
			return condition == null ? true : condition.isTrue(contact);
		}
	}

	private List<ChatTemplate> chatTemplates = new ArrayList<>();

	enum REPLACMENT {
		EMOJI_DANCING(contact -> contact.getGender() != null && contact.getGender() == 1 ? "🕺🏻" : "💃"),
		EMOJI_WAVING(contact -> contact.getGender() != null && contact.getGender() == 1 ? "🙋🏻‍♂️" : "🙋‍♀️"),
		CONTACT_PSEUDONYM(contact -> contact.getPseudonym()),
		CONTACT_CURRENT_TOWN(
				contact -> {
					try {
						return externalService.googleAddress(contact.getLatitude(), contact.getLongitude()).getTown();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}),
		CONTACT_VERSION(contact -> contact.getVersion()),
		VERSION(contact -> currentVersion);

		private static interface Exec {
			String replace(Contact contact);
		}

		private final Exec exec;

		private REPLACMENT(Exec exec) {
			this.exec = exec;
		}

		String replace(String s, Contact contact) {
			return s.contains(name()) ? s.replaceAll(name(), exec.replace(contact)) : s;
		}
	}

	public EngagementService() {
		chatTemplates.add(new ChatTemplate(Text.engagement_uploadProfileImage,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getImage() == null));

		chatTemplates.add(new ChatTemplate(Text.engagement_becomeGuide,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getGuide() == null || !contact.getGuide()));

		chatTemplates.add(new ChatTemplate(Text.engagement_guide,
				"pageInfo.socialShare()",
				contact -> contact.getGuide() != null && contact.getGuide()));

		chatTemplates.add(new ChatTemplate(Text.engagement_bluetoothMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textId='FindMe' and contactNotification.contactId=" + contact.getId());
					return repository.list(params).size() > 2;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_bluetoothNoMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textId='FindMe' and contactNotification.contactId=" + contact.getId());
					return repository.list(params).size() == 0;
				}));

		chatTemplates.add(new ChatTemplate(Text.engagement_addFriends,
				"pageInfo.socialShare()",
				null));

		chatTemplates.add(new ChatTemplate(Text.engagement_installCurrentVersion,
				"global.openStore()",
				contact -> contact.getOs() != OS.web && currentVersion.compareTo(contact.getVersion()) > 0));

		chatTemplates.add(new ChatTemplate(Text.engagement_newTown,
				"pageInfo.socialShare()",
				contact -> contact.getLatitude() != null
						&& contact.getModifiedAt() != null
						&& contact.getModifiedAt()
								.after(new Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli()))));

		chatTemplates.add(new ChatTemplate(Text.engagement_like, null, null));
	}

	public void sendSpontifyEmail() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 17)
			return;
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.verified=true and contact.version is null");
		params.setLimit(0);
		final Result list = repository.list(params);
		final Contact admin = repository.one(Contact.class, adminId);
		final Map<String, String> text = new HashMap<>();
		String value = "";
		text.put("DE", "Das Beste kommt spontan!\n\n"
				+ "findapp war schon immer auf Spontanität aus, nun tragen wir dem auch im Namen Rechnung. Aus findapp wird\n\n"
				+ "spontify.me\n\n"
				+ "nicht nur eine Umbenennung, sondern ein komplettes Redesign, einfachste Bedienung und ein modernes Look & Feel.\n\n"
				+ "Schau rein, überzeuge Dich und wenn Dir die App gefällt, empfehle uns weiter. "
				+ "Je großer die Community, desto mehr spontane Events und Begegnungen werden hier möglich.\n\n"
				+ "Wir freuen uns auf Feedback. Einfach hier antworten oder in der App mir schreiben.\n\n"
				+ "Liebe Grüße und bleib gesund!\n"
				+ "Sponti Support");
		text.put("EN", "The best comes spontaneously!\n\n"
				+ "findapp has always been about spontaneity, now we take that into account in the name. findapp becommes\n\n"
				+ "spontify.me\n\n"
				+ "not only a renaming, but a complete redesign, simple operation and a modern look & feel.\n\n"
				+ "Take a look, convince yourself and if you like the app, recommend us. "
				+ "The larger the community, the more spontaneous events and encounters are possible here.\n\n"
				+ "We look forward to feedback. Just reply here or write to me in the app.\n\n"
				+ "Greetings and stay healthy!\n"
				+ "Sponti Support");
		for (int i = 0; i < list.size(); i++) {
			final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
			notificationService.sendNotificationEmail(admin, to, text.get(to.getLanguage()),
					"https://blog.spontify.me");
			value += "|" + to.getId();
		}
		if (value.length() > 0) {
			Setting s = new Setting();
			s.setLabel("findapp-spontify-email");
			s.setValue(value.substring(1));
			repository.save(s);
		}
	}

	public void sendRegistrationReminder() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 19)
			return;
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.verified=false");
		params.setLimit(0);
		final Result list = repository.list(params);
		String value = "";
		for (int i = 0; i < list.size(); i++) {
			final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
			authenticationService.recoverSendEmailReminder(to);
			value += "|" + to.getId();
		}
		if (value.length() > 0) {
			final Setting s = new Setting();
			s.setLabel("registration-reminder");
			s.setValue(value.substring(1));
			repository.save(s);
		}
	}

	public void sendChats() throws Exception {
		if (currentVersion == null)
			currentVersion = (String) repository.one(new QueryParams(Query.contact_maxAppVersion)).get("c");
		QueryParams params = new QueryParams(Query.contact_listChatFlat);
		params.setLimit(0);
		params.setSearch("chat.textId='" + Text.engagement_installCurrentVersion.name() +
				"' and contact.version='" + currentVersion + "'");
		Result ids = repository.list(params);
		for (int i = 0; i < ids.size(); i++) {
			final Chat chat = repository.one(Chat.class, (BigInteger) ids.get(i));
			chat.setTextId(null);
			repository.save(chat);
		}
		params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.id<>" + adminId + " and contact.verified=true and contact.version is not null");
		ids = repository.list(params);
		params = new QueryParams(Query.contact_chat);
		for (int i = 0; i < ids.size(); i++) {
			params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + ids.get(i).get("contact.id")
					+ " and chat.createdAt>'"
					+ Instant.now().minus(Duration.ofDays(7 + (int) (Math.random() * 4)))
							.minus(Duration.ofHours((int) (Math.random() * 12))).toString()
					+ '\'');
			if (repository.list(params).size() == 0) {
				final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				final Instant d = Instant.now();
				d.minus(Duration.ofMinutes(
						contact.getTimezoneOffset() == null ? -60 : contact.getTimezoneOffset().longValue()));
				if (d.get(ChronoField.HOUR_OF_DAY) < 7 || d.get(ChronoField.HOUR_OF_DAY) > 21)
					break;
				for (ChatTemplate chatTemplate : chatTemplates) {
					if (chatTemplate.eligible(contact)) {
						params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
								+ " and chat.textId='" + chatTemplate.textId.name() + '\'');
						if (repository.list(params).size() == 0) {
							String s = chatTemplate.textId.getText(contact.getLanguage());
							for (REPLACMENT rep : REPLACMENT.values())
								s = rep.replace(s, contact);
							final Chat chat = new Chat();
							chat.setContactId(adminId);
							chat.setContactId2(contact.getId());
							chat.setSeen(false);
							chat.setAction(chatTemplate.action);
							chat.setTextId(chatTemplate.textId.name());
							chat.setNote(s);
							repository.save(chat);
							break;
						}
					}
				}
			}
		}
	}
}