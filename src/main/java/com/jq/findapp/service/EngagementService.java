package com.jq.findapp.service;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import com.jq.findapp.entity.Chat;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService.NotificationID;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EngagementService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

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
						+ ", wie gefällt Dir findapp? 🤔\nLust mit mir drüber zu chatten? " + REPLACMENT.EMOJI_WAVING,
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