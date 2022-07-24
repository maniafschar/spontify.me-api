package com.jq.findapp.service;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;
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

	@Autowired
	private void setExternalService(ExternalService externalService) {
		EngagementService.externalService = externalService;
	}

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static class ChatTemplate {
		private final String text;
		private final Condition condition;

		interface Condition {
			boolean isTrue(Contact contact);
		}

		private ChatTemplate(String text, Condition condition) {
			this.text = text;
			this.condition = condition;
		}

		boolean elagable(Contact contact) {
			return condition == null ? true : condition.isTrue(contact);
		}
	}

	private List<ChatTemplate> chatTemplates = new ArrayList<>();

	enum REPLACMENT {
		EMOJI_DANCING(contact -> contact.getGender() == 1 ? "🕺🏻" : "💃"),
		EMOJI_WAVING(contact -> contact.getGender() == 1 ? "🙋🏻‍♂️" : "🙋‍♀️"),
		CONTACT_PSEUDONYM(contact -> contact.getPseudonym()),
		CONTACT_CURRENT_TOWN(
				contact -> {
					try {
						return externalService.googleAddress(contact.getLatitude(), contact.getLongitude()).getTown();
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

		private static interface Exec {
			String replace(Contact contact);
		}

		private final Exec exec;

		private REPLACMENT(Exec exec) {
			this.exec = exec;
		}

		String replace(String s, Contact contact) {
			return s.replaceAll(name(), exec.replace(contact));
		}
	}

	public EngagementService() {
		chatTemplates.add(new ChatTemplate(
				"Hallo " + REPLACMENT.CONTACT_PSEUDONYM
						+ ", wie gefällt Dir sonptify.me? 🤔\nLust mit mir drüber zu chatten? "
						+ REPLACMENT.EMOJI_WAVING,
				null));

		chatTemplates.add(new ChatTemplate(
				"[[pageChat.close(event,function(){ui.navigation.goTo(&quot;settings&quot;)})]]Hey "
						+ REPLACMENT.CONTACT_PSEUDONYM + ","
						+ "\nals Guide zeigst Du anderen Deine Sicht der Stadt und erhältst besonders passende Vorschläge, um Deinen Bekanntenkreis zu erweitern. Möchtest Du ein Guide werden? "
						+ REPLACMENT.EMOJI_DANCING + "\nSetze einfach das Häckchen in den Einstellungen "
						+ REPLACMENT.EMOJI_WAVING,
				contact -> contact.getGuide() == null || !contact.getGuide()));

		chatTemplates.add(new ChatTemplate(
				"[[pageInfo.socialShare()]]Hey " + REPLACMENT.CONTACT_PSEUDONYM + ","
						+ "\nDu bist Guide, möchtest Du Deine Freunde einladen, damit die Community wächst? "
						+ REPLACMENT.EMOJI_DANCING,
				contact -> contact.getGuide() != null && contact.getGuide()));

		chatTemplates.add(new ChatTemplate("[[pageInfo.socialShare()]]Hey " +
				REPLACMENT.CONTACT_PSEUDONYM
				+ ",\nDu hattest schon ein paar Bluetooth Matches. Hast Du sie angesprochen?\nLade Deine Freunde ein, damit die Community wächst und mehr Matches sich ergeben. "
				+ REPLACMENT.EMOJI_DANCING,
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textId='FindMe' and contactNotification.contactId=" + contact.getId());
					return repository.list(params).size() > 2;
				}));

		chatTemplates.add(new ChatTemplate("[[pageInfo.socialShare()]]Hey " +
				REPLACMENT.CONTACT_PSEUDONYM
				+ ",\nDu hast noch keinen Bluetooth Match. Möchtest Du Deine Freunde einladen, damit die Community wächst und mehr Matches sich ergeben. "
				+ REPLACMENT.EMOJI_DANCING,
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textId='FindMe' and contactNotification.contactId=" + contact.getId());
					return repository.list(params).size() == 0;
				}));

		chatTemplates.add(new ChatTemplate(
				"[[pageInfo.socialShare()]]Hey " + REPLACMENT.CONTACT_PSEUDONYM + ","
						+ "\nLust Deine Frende hinzuzufügen? " + REPLACMENT.EMOJI_DANCING + "\nLade sie einfach ein... "
						+ REPLACMENT.EMOJI_WAVING,
				contact -> contact.getVersion() != null && contact.getVersion().compareTo("0.8.1") >= 0));

		chatTemplates.add(new ChatTemplate("[[pageInfo.socialShare()]]Hey " +
				REPLACMENT.CONTACT_PSEUDONYM + ","
				+ "\nwir möchten in " + REPLACMENT.CONTACT_CURRENT_TOWN
				+ " wachsen und sind auf der Suche nach sympathischen Kotakten. Kennst Du vielleicht ein paar, die Du hier einladen könntest? "
				+ REPLACMENT.EMOJI_WAVING,
				contact -> contact.getModifiedAt().getTime() > System.currentTimeMillis() - 24 * 60 * 60 * 1000));
	}

	public void sendWelcomeChat() throws Exception {
		QueryParams params = new QueryParams(Query.contact_listId);
		final GregorianCalendar gc = new GregorianCalendar();
		gc.add(Calendar.DATE, -1);
		params.setSearch("contact.modifiedAt>'"
				+ gc.get(Calendar.YEAR) + '-' + (gc.get(Calendar.MONTH) + 1) + '-' + gc.get(Calendar.DATE) + "'");
		params.setLimit(0);
		final Result list = repository.list(params);
		params = new QueryParams(Query.contact_chat);
		for (int i = 0; i < list.size(); i++) {
			params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + list.get(i).get("contact.id"));
			if (repository.list(params).size() == 0) {
				final Contact contact = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
				final Chat chat = new Chat();
				chat.setContactId(adminId);
				chat.setContactId2(contact.getId());
				chat.setSeen(false);
				chat.setNote(
						MessageFormat.format(Text.mail_welcome.getText(contact.getLanguage()),
								contact.getPseudonym()));
				repository.save(chat);
			}
		}
	}

	public void sendSpontifyEmail() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 17)
			return;
		gc.add(Calendar.DATE, -7);
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setUser(repository.one(Contact.class, adminId));
		params.setSearch(
				"contact.verified=true and (contact.version is null or contact.version not like '0.1.%') and (contact.modifiedAt is null or contact.modifiedAt<'"
						+ gc.get(Calendar.YEAR) + '-' + (gc.get(Calendar.MONTH) + 1) + '-' + gc.get(Calendar.DATE)
						+ "')");
		params.setLimit(0);
		final Result list = repository.list(params);
		final Contact admin = repository.one(Contact.class, adminId);
		final Map<String, String> text = new HashMap<>();
		String value = "";
		text.put("DE", "Die schönsten Ereignisse des Lebens passieren spontan.\n\n"
				+ "findapp war schon immer auf Spontanität aus, nun tragen wir dem auch im Namen Rechnung. Aus findapp wird\n\n"
				+ "spontify.me\n\n"
				+ "nicht nur eine Umbenennung, sondern ein komplettes Redesign, einfachste Bedienung und ein modernes Look & Feel.\n\n"
				+ "Schau rein, überzeuge Dich und wenn Dir die App gefällt, empfehle uns weiter. "
				+ "Je großer die Community, desto mehr spontane Events und Begegnungen werden hier möglich.\n\n"
				+ "Wir freuen uns auf Feedback. Einfach hier antworten oder in der App mir schreiben.\n\n"
				+ "Liebe Grüße und bleib gesund!\n"
				+ "Susi Support");
		text.put("EN", "The most beautiful events in life happen spontaneously.\n\n"
				+ "findapp has always been about spontaneity, now we take that into account in the name. findapp becommes\n\n"
				+ "spontify.me\n\n"
				+ "not only a renaming, but a complete redesign, simple operation and a modern look & feel.\n\n"
				+ "Take a look, convince yourself and if you like the app, recommend us. "
				+ "The larger the community, the more spontaneous events and encounters are possible here.\n\n"
				+ "We look forward to feedback. Just reply here or write to me in the app.\n\n"
				+ "Greetings and stay healthy!\n"
				+ "Susi Support");
		for (int i = 0; i < list.size(); i++) {
			final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
			notificationService.sendNotificationEmail(admin, to, text.get(to.getLanguage()),
					"https://blog.spontify.me");
			value += "\u0015" + to.getId();
		}
		if (value.length() > 0) {
			Setting s = new Setting();
			s.setLabel("findapp-spontify-email");
			s.setValue(value.substring(1));
			repository.save(s);
		}
	}

	public void sendVerifyEmail() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 19)
			return;
		gc.add(Calendar.DATE, -7);
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setUser(repository.one(Contact.class, adminId));
		params.setSearch("contact.verified=false");
		params.setLimit(0);
		final Result list = repository.list(params);
		String value = "";
		for (int i = 0; i < list.size(); i++) {
			final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
			authenticationService.recoverSendReminder(to);
			value += "\u0015" + to.getId();
		}
		if (value.length() > 0) {
			final Setting s = new Setting();
			s.setLabel("verify-email");
			s.setValue(value.substring(1));
			repository.save(s);
		}
	}

	public void sendChats() throws Exception {
		final Calendar now = new GregorianCalendar();
		if (now.get(Calendar.HOUR_OF_DAY) < 9 || now.get(Calendar.HOUR_OF_DAY) > 1)
			return;
		final Contact susi = repository.one(Contact.class, adminId);
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setUser(susi);
		params.setSearch(
				"contact.id<>3 and contact.verified>0 and TO_DAYS(contact.createdAt)+3<TO_DAYS(current_timestamp) and (select max(id) from chat where contactId2=3 and contactId=contact.id and TO_DAYS(createdAt)+7>TO_DAYS(current_timestamp)) is null");
		final Result contactIds = repository.list(params);
		for (int i = 0; i < contactIds.size(); i++) {
			final Contact contact = repository.one(Contact.class, (BigInteger) contactIds.get(i).get("contact.id"));
			for (ChatTemplate chatTemplate : chatTemplates) {
				if (chatTemplate.elagable(contact)) {
					String s = chatTemplate.text;
					for (REPLACMENT rep : REPLACMENT.values())
						s = rep.replace(s, contact);
					final Chat chat = new Chat();
					chat.setContactId(adminId);
					chat.setContactId2(contact.getId());
					chat.setNote(s);
					repository.save(chat);
					notificationService.sendNotification(susi, contact,
							NotificationID.mgMarketing,
							"DE".equals(contact.getLanguage()) ? "Susi Support hat Dir geschrieben"
									: "Susi Support sent you a message",
							Strings.encodeParam("chat=3"));
					return;
				}
			}
		}
	}
}