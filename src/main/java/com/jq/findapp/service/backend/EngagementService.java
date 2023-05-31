package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class EngagementService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private Text text;

	private static String currentVersion;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static class ChatTemplate {
		private final TextId textId;
		private final String action;
		private final Condition condition;

		interface Condition {
			boolean isTrue(Contact contact);
		}

		private ChatTemplate(final TextId textId, final String action, final Condition condition) {
			this.textId = textId;
			this.condition = condition;
			this.action = action;
		}

		boolean eligible(final Contact contact) {
			return condition == null ? true : condition.isTrue(contact);
		}
	}

	private final List<ChatTemplate> chatTemplates = new ArrayList<>();
	private final QueryParams paramsAdminBlocked = new QueryParams(Query.contact_block);

	enum REPLACMENT {
		EMOJI_DANCING((contact, location, externalService,
				repository) -> contact.getGender() != null && contact.getGender() == 1 ? "🕺🏻" : "💃"),
		EMOJI_WAVING(
				(contact, location, externalService,
						repository) -> contact.getGender() != null && contact.getGender() == 1 ? "🙋🏻‍♂️" : "🙋‍♀️"),
		CONTACT_MODIFIED_AT((contact, location, externalService, repository) -> Strings.formatDate(null,
				contact.getModifiedAt(), contact.getTimezone())),
		CONTACT_PSEUDONYM((contact, location, externalService, repository) -> contact.getPseudonym()),
		CONTACT_CURRENT_TOWN((contact, location, externalService, repository) -> {
			try {
				return externalService.getAddress(contact.getLatitude(), contact.getLongitude(), null).getTown();
			} catch (

		final Exception e) {
				throw new RuntimeException(e);
			}
		}),

		CONTACT_VERSION((contact, location, externalService, repository) -> contact.getVersion()),
		LOCATION_NAME((contact, location, externalService, repository) -> location.getName()),
		NEW_CONTACTS_COUNT((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			return "" + repository.list(params).size();
		}),
		NEW_CONTACTS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			final Result result = repository.list(params);
			for (int i = result.size() - 1; i >= 0; i--) {
				if (result.get(i).get("_geolocationDistance") instanceof Number)
					return "" + (int) (((Number) result.get(i).get("_geolocationDistance")).doubleValue() + 0.5);
			}
			return "11";
		}),
		NEW_LOCATIONS_COUNT((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			return "" + repository.list(params).size();
		}),
		NEW_LOCATIONS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
			final Result result = repository.list(params);
			return ""
					+ (int) (((Number) result.get(result.size() - 1).get("_geolocationDistance")).doubleValue() + 0.5);
		}),
		VERSION((contact, location, externalService, repository) -> currentVersion);

		private static interface Exec {
			String replace(Contact contact, Location location, ExternalService externalService, Repository repository);
		}

		private final Exec exec;

		private REPLACMENT(final Exec exec) {
			this.exec = exec;
		}

		String replace(final String s, final Contact contact, final Location location,
				final ExternalService externalService,
				final Repository repository) {
			return s.contains(name())
					? s.replaceAll(name(), exec.replace(contact, location, externalService, repository))
					: s;
		}

	}

	public EngagementService() {
		chatTemplates.add(new ChatTemplate(TextId.engagement_uploadProfileImage,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getImage() == null));

		chatTemplates.add(new ChatTemplate(TextId.engagement_uploadProfileAttributes,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> (Strings.isEmpty(contact.getSkills()) && Strings.isEmpty(contact.getSkillsText()))
						|| (Strings.isEmpty(contact.getAgeMale()) && Strings.isEmpty(contact.getAgeFemale())
								&& Strings.isEmpty(contact.getAgeDivers()))
						|| contact.getGender() == null || contact.getBirthday() == null));

		chatTemplates.add(new ChatTemplate(TextId.engagement_newTown,
				"pageInfo.socialShare()",
				contact -> contact.getLatitude() != null
						&& contact.getModifiedAt() != null
						&& contact.getModifiedAt()
								.after(new Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli()))));

		chatTemplates.add(new ChatTemplate(TextId.engagement_allowLocation,
				"",
				contact -> contact.getLongitude() == null && contact.getOs() != OS.web));

		chatTemplates.add(new ChatTemplate(TextId.engagement_addEvent,
				"",
				contact -> {
					final QueryParams params = new QueryParams(Query.event_list);
					params.setUser(contact);
					params.setSearch("event.contactId=" + contact.getId());
					if (repository.list(params).size() == 0) {
						params.setQuery(Query.location_listFavorite);
						params.setSearch("locationFavorite.contactId=" + contact.getId());
						return repository.list(params).size() > 0;
					}
					return false;
				}));

		chatTemplates.add(new ChatTemplate(TextId.engagement_newLocations,
				"",
				contact -> {
					if (contact.getLongitude() == null)
						return false;
					final QueryParams params = new QueryParams(Query.location_list);
					params.setUser(contact);
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setSearch(
							"location.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
					return repository.list(params).size() > 1;
				}));

		chatTemplates.add(new ChatTemplate(TextId.engagement_newContacts,
				"",
				contact -> {
					if (contact.getLongitude() == null)
						return false;
					final QueryParams params = new QueryParams(Query.contact_list);
					params.setUser(contact);
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setDistance(50);
					params.setSearch(
							"contact.createdAt>='" + Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "'");
					return repository.list(params).size() > 9;
				}));

		chatTemplates.add(new ChatTemplate(TextId.engagement_bluetoothMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textType='FindMe' and contactNotification.contactId="
									+ contact.getId());
					return repository.list(params).size() > 2;
				}));

		chatTemplates.add(new ChatTemplate(TextId.engagement_bluetoothNoMatch,
				"pageInfo.socialShare()",
				contact -> {
					final QueryParams params = new QueryParams(Query.contact_notification);
					params.setUser(contact);
					params.setSearch(
							"contactNotification.textType='FindMe' and contactNotification.contactId="
									+ contact.getId());
					return repository.list(params).size() == 0;
				}));

		chatTemplates.add(new ChatTemplate(TextId.engagement_addFriends,
				"pageInfo.socialShare()",
				null));

		chatTemplates.add(new ChatTemplate(TextId.engagement_installCurrentVersion,
				"global.openStore()",
				contact -> !Strings.isEmpty(contact.getVersion()) && contact.getOs() != OS.web
						&& currentVersion.compareTo(contact.getVersion()) > 0));

		chatTemplates.add(new ChatTemplate(TextId.engagement_praise,
				"",
				contact -> contact.getLongitude() != null && contact.getImage() != null
						&& contact.getBirthday() != null && !Strings.isEmpty(contact.getDescription())
						&& !Strings.isEmpty(contact.getSkills())
						&& (!Strings.isEmpty(contact.getAgeDivers()) || !Strings.isEmpty(contact.getAgeFemale())
								|| !Strings.isEmpty(contact.getAgeMale()))));

		chatTemplates.add(new ChatTemplate(TextId.engagement_patience,
				"pageInfo.socialShare()",
				contact -> contact.getLongitude() != null));

		chatTemplates.add(new ChatTemplate(TextId.engagement_like, null, null));
	}

	public SchedulerResult sendRegistrationReminder() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sendRegistrationReminder");
		try {
			final GregorianCalendar gc = new GregorianCalendar();
			if (gc.get(Calendar.HOUR_OF_DAY) < 9 || gc.get(Calendar.HOUR_OF_DAY) > 18)
				return result;
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.createdAt<'" + Instant.now().minus(Duration.ofHours(3))
					+ "' and contact.verified=false and contact.notificationEngagement=true");
			params.setLimit(0);
			final Result list = repository.list(params);
			final long DAY = 86400000;
			String value = "", failedEmails = "";
			params.setQuery(Query.misc_setting);
			try {
				for (int i = 0; i < list.size(); i++) {
					final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
					params.setSearch("setting.label='registration-reminder' and concat('|', setting.data, '|') like '%|"
							+ to.getId() + "|%'");
					final Result result2 = repository.list(params);
					if (result2.size() == 0
							|| ((Timestamp) result2.get(0).get("setting.createdAt")).getTime() + 9 * DAY < System
									.currentTimeMillis()) {
						try {
							authenticationService.recoverSendEmailReminder(to);
							value += "|" + to.getId();
							if (value.length() > Setting.MAX_VALUE_LENGTH - 20)
								break;
							Thread.sleep(10000);
						} catch (final MailSendException ex) {
							failedEmails += "\n" + to.getEmail() + "\n" + Strings.stackTraceToString(ex);
						}
					}
				}
			} finally {
				if (value.length() > 0) {
					final Setting s = new Setting();
					s.setLabel("registration-reminder");
					s.setData(value.substring(1));
					repository.save(s);
					result.result = "" + value.split("|").length;
				} else
					result.result = "0";
				if (failedEmails.length() > 0)
					notificationService.createTicket(TicketType.ERROR, "sendRegistrationReminder",
							"Failed Emails:" + failedEmails, null);
			}
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult sendChats() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sendChats");
		try {
			resetChatInstallCurrentVersion();
			sendAfterworkEmail();
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setLimit(0);
			params.setSearch("contact.id<>" + adminId
					+ " and contact.verified=true and contact.version is not null");
			final Result ids = repository.list(params);
			params.setQuery(Query.contact_chat);
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2="
						+ ids.get(i).get("contact.id"));
				if (repository.list(params).size() == 0) {
					sendChat(TextId.engagement_welcome,
							repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")), null, null);
					count++;
				}
			}
			result.result += "welcome: " + count;
			count = 0;
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (isTimeForNewChat(contact, params, false) && sendChatTemplate(contact))
					count++;
			}
			result.result += "\ntemplate: " + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private void sendAfterworkEmail() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setLimit(0);
		params.setSearch("contact.clientId=1 and contact.createdAt<'2023-04-26 00:00:00'");
		final Result list = repository.list(params);
		final Contact admin = repository.one(Contact.class, adminId);
		final Map<String, String> text = new HashMap<>();
		text.put("DE",
				"<span style=\"text-align:left;\">Nach der Arbeit ist vor der Arbeit? Vergesst den <span style=\"color:--bg2stop;\">afterwork</span> nicht!\n"
						+ "Nach der Arbeit gibt es viel zu erleben. Was magst Du?"
						+ "<ul><li>Mountainbiken?</li>"
						+ "<li>Exotisch kochen?</li>"
						+ "<li>Über Nietzsche philosophieren?</li>"
						+ "<li>Bei einem Gläschen Wein Dein Französisch vertiefen?</li>"
						+ "<li>Karten spielen?</li>"
						+ "<li>Konzerte besuchen?</li></ul>"
						+ "In <span style=\"color:--bg2stop;\">afterwork</span> findest Du nicht nur passende Events, Du kannst selbst auch <span style=\"color:--bg2stop;\">Suchanfragen</span> einstellen, um spontan Gleichgesinnte zu finden.\n\n"
						+ "afterwork ist nicht nur kostenlos, Du kannst sogar <span style=\"color:--bg2stop;\">Geld verdienen</span>. Baue Dir Deine Community auf, veranstalte tolle Events, erweitere Deinen Bekanntheitsgrad. Sind Teilnehmer bereit, für Dein Event zu bezahlen, dann kannst Du mit Deinem Paypal Account kostenpflichtige Events einstellen. Die Bezahlung der Teilnehmer landet, abzüglich unserer Gebühr von 20%, direkt und sofort auf Dein Paypal Account.\n\n"
						+ "Du erhältst die Email, weil wir unseren Namen spontify&#8203;.&#8203;me geändert haben und Du Dich dort registriert hast. Außer der Namensgebung hat sich rechtlich nichts geändert. Wir sind weiterhin:"
						+ "<ul><li>Open Source</li>"
						+ "<li>Verkaufen oder geben auf anderer Weise keine Deiner Daten weiter</li>"
						+ "<li>Jeder ist Premiun ohne Wenn und Aber</li>"
						+ "<li>Verdienen unsere Geld lediglich durch eine Gebühr bei kostenpflichtigen Events</li></ul>"
						+ "</span>");
		text.put("EN",
				"<span style=\"text-align:left;\">After work is before work? Don't forget the afterwork!\n"
						+ "There is a lot to do after work. What do you like?"
						+ "<ul><li>mountain biking?</li>"
						+ "<li>Philosophize about Nietzsche?</li>"
						+ "<li>Would you like to improve your French over a glass of wine?</li>"
						+ "<li>Playing cards?</li>"
						+ "<li>To visit concerts?</li></ul>"
						+ "In <span style=\"color:--bg2stop;\">afterwork</span> you will not only find suitable events, you can also set up <span style=\"color:--bg2stop;\">search queries</span> to spontaneously find like-minded people.\n\n"
						+ "afterwork is not only free, you can even <span style=\"color:--bg2stop;\">earn money</span>. Build up your community, organize great events, increase your level of awareness. If participants are willing to pay for your event, you can use your PayPal account to set up fee-based events. The payment of the participants, minus our fee of 20%, ends up directly and immediately on your PayPal account.\n\n"
						+ "You are receiving the email because we have changed our name to spontify&#8203;.&#8203;me and you have registered there. Apart from the naming, nothing has changed legally. We are still:"
						+ "<ul><li>open-source</li>"
						+ "<li>Do not sell or otherwise give away any of your information</li>"
						+ "<li>Everyone is premium without ifs and buts</li>"
						+ "<li>Earn our money only through a fee at paid events</li></ul>"
						+ "</span>");
		params.setQuery(Query.misc_setting);
		final Setting s = new Setting();
		String value = "";
		s.setLabel("rename-afterwork");
		try {
			for (int i = 0; i < list.size(); i++) {
				final Contact to = repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
				params.setSearch("setting.label='rename-afterwork' and concat('|', setting.data, '|') like '%|"
						+ to.getId() + "|%'");
				final Result result = repository.list(params);
				if (result.size() == 0) {
					notificationService.sendNotificationEmail(admin, to, text.get(to.getLanguage()),
							"https://after-work.events");
					value += "|" + to.getId();
					if (value.length() > Setting.MAX_VALUE_LENGTH - 20)
						break;
					Thread.sleep(10000);
				}
			}
		} finally {
			if (value.length() > 0) {
				s.setData(value.substring(1));
				repository.save(s);
			}
		}
	}

	private boolean isTimeForNewChat(final Contact contact, final QueryParams params, final boolean nearBy) {
		final int hour = Instant.now().atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId()).getHour();
		if (hour > 6 && hour < 22) {
			paramsAdminBlocked.setSearch("block.contactId=" + adminId + " and block.contactId2="
					+ contact.getId()
					+ " or block.contactId=" + contact.getId() + " and block.contactId2=" + adminId);
			if (repository.list(paramsAdminBlocked).size() == 0) {
				params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contact.getId()
						+ " and contactChat.createdAt>'"
						+ Instant.now().minus(Duration.ofDays(15 + (int) (Math.random() * 3)))
								.minus(Duration.ofHours((int) (Math.random() * 12))).toString()
						+ '\'');
				if (repository.list(params).size() == 0) {
					params.setSearch(
							"contactChat.textId is not null and contactChat.contactId=" + adminId
									+ " and contactChat.contactId2="
									+ contact.getId());
					final Result lastChats = repository.list(params);
					if (lastChats.size() == 0)
						return true;
					final boolean isLastChatNearBy = ((TextId) lastChats.get(0).get("contactChat.textId")).name()
							.startsWith(
									TextId.engagement_nearByLocation.name().substring(0,
											TextId.engagement_nearByLocation.name().indexOf('L')));
					if (!nearBy && isLastChatNearBy || nearBy && !isLastChatNearBy)
						return true;
					if (((Timestamp) lastChats.get(0).get("contactChat.createdAt"))
							.before(new Timestamp(Instant.now().minus(Duration.ofDays(7)).toEpochMilli())))
						return true;
				}
			}
		}
		return false;
	}

	private void resetChatInstallCurrentVersion() throws Exception {
		if (currentVersion == null) {
			final QueryParams params = new QueryParams(Query.contact_maxAppVersion);
			params.setUser(repository.one(Contact.class, adminId));
			currentVersion = (String) repository.one(params).get("_c");
		}
		final QueryParams params = new QueryParams(Query.contact_listChatFlat);
		params.setLimit(0);
		params.setSearch("contactChat.textId='" + TextId.engagement_installCurrentVersion.name() +
				"' and contact.version='" + currentVersion + "'");
		final Result ids = repository.list(params);
		for (int i = 0; i < ids.size(); i++) {
			final ContactChat contactChat = repository.one(ContactChat.class,
					(BigInteger) ids.get(i).get("contactChat.id"));
			contactChat.setTextId(null);
			repository.save(contactChat);
		}
	}

	public SchedulerResult sendNearBy() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sendNearBy");
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch(
					"contact.id<>" + adminId + " and contact.verified=true and contact.notificationEngagement=true and "
							+ "contact.version is not null and contact.longitude is not null and "
							+ "(length(contact.skills)>0 or length(contact.skillsText)>0)");
			params.setLimit(0);
			final Result ids = repository.list(params);
			params.setQuery(Query.contact_chat);
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (isTimeForNewChat(contact, params, true)) {
					final String action = getLastNearByAction(params, (BigInteger) ids.get(i).get("contact.id"));
					if (!action.startsWith("p=") && sendContact(contact))
						count++;
				}
			}
			result.result = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private String getLastNearByAction(final QueryParams params, final BigInteger id) {
		params.setSearch("contactChat.action is not null and contactChat.textId like '"
				+ TextId.engagement_nearByLocation.name().substring(0,
						TextId.engagement_nearByLocation.name().indexOf('L'))
				+ "%' and contactChat.contactId=" + adminId + " and contactChat.contactId2=" + id);
		final Result chats = repository.list(params);
		if (chats.size() == 0)
			return "";
		String action = (String) chats.get(0).get("contactChat.action");
		if (action == null)
			return "";
		if (action.contains("&quot;"))
			action = action.substring(action.indexOf("&quot;") + 6, action.lastIndexOf("&quot;"));
		if (action.contains("="))
			action = action.substring(0, action.lastIndexOf('='));
		return new String(Base64.getDecoder().decode(action), StandardCharsets.UTF_8);
	}

	private boolean sendChatTemplate(final Contact contact) throws Exception {
		for (final ChatTemplate chatTemplate : chatTemplates) {
			if (chatTemplate.eligible(contact) &&
					sendChat(chatTemplate.textId, contact, null, chatTemplate.action))
				return true;
		}
		return false;
	}

	private boolean sendContact(final Contact contact) throws Exception {
		final String search = Score.getSearchContact(contact);
		if (search.length() > 0) {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setSearch("contactLink.id is null and contact.id<>" + contact.getId()
					+ " and contact.image is not null and (" + search + ")");
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setUser(contact);
			final Result result = repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			double score = 0;
			Contact contact2 = null;
			for (int i = 0; i < result.size(); i++) {
				final Contact c = repository.one(Contact.class, (BigInteger) result.get(i).get("contact.id"));
				params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contact.getId()
						+ " and contactChat.textId='" + TextId.engagement_nearByContact.name()
						+ "' and contactChat.action like '%"
						+ Strings.encodeParam("p=" + c.getId()) + "%'");
				if (repository.list(params).size() == 0) {
					final double s = Score.getContact(contact, c);
					if (s > score) {
						score = s;
						contact2 = c;
					}
				}
			}
			if (score > 0.5 && contact2 != null) {
				final Location location = new Location();
				location.setName(contact2.getPseudonym());
				sendChat(TextId.engagement_nearByContact, contact, location,
						"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("p=" + contact2.getId())
								+ "&quot;,event)");
				return true;
			}
		}
		return false;
	}

	public boolean sendChat(final TextId textId, final Contact contact, final Location location, final String action)
			throws Exception {
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contact.getId()
				+ " and contactChat.textId='" + textId.name() + '\'');
		if (repository.list(params).size() == 0) {
			String s = text.getText(contact, textId);
			for (final REPLACMENT rep : REPLACMENT.values())
				s = rep.replace(s, contact, location, externalService, repository);
			final ContactChat contactChat = new ContactChat();
			contactChat.setContactId(adminId);
			contactChat.setContactId2(contact.getId());
			contactChat.setSeen(false);
			contactChat.setAction(action);
			contactChat.setTextId(textId);
			contactChat.setNote(s);
			try {
				repository.save(contactChat);
				return true;
			} catch (final IllegalArgumentException ex) {
				if (!"duplicate chat".equals(ex.getMessage()))
					throw new RuntimeException(ex);
			}
		}
		return false;
	}
}