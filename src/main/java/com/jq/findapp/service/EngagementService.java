package com.jq.findapp.service;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Setting;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
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
	private final QueryParams paramsAdminBlocked = new QueryParams(Query.contact_block);

	enum REPLACMENT {
		EMOJI_DANCING((contact, location) -> contact.getGender() != null && contact.getGender() == 1 ? "🕺🏻" : "💃"),
		EMOJI_WAVING(
				(contact, location) -> contact.getGender() != null && contact.getGender() == 1 ? "🙋🏻‍♂️" : "🙋‍♀️"),
		CONTACT_PSEUDONYM((contact, location) -> contact.getPseudonym()),
		CONTACT_CURRENT_TOWN((contact, location) -> {
			try {
				return externalService.googleAddress(contact.getLatitude(), contact.getLongitude()).getTown();
			} catch (

		Exception e) {
				throw new RuntimeException(e);
			}
		}),

		CONTACT_VERSION((contact, location) -> contact.getVersion()),
		LOCATION_NAME((contact, location) -> location.getName()),
		VERSION((contact, location) -> currentVersion);

		private static interface Exec {
			String replace(Contact contact, Location location);
		}

		private final Exec exec;

		private REPLACMENT(Exec exec) {
			this.exec = exec;
		}

		String replace(String s, Contact contact, Location location) {
			return s.contains(name()) ? s.replaceAll(name(), exec.replace(contact, location)) : s;
		}

	}

	public EngagementService() {
		chatTemplates.add(new ChatTemplate(Text.engagement_uploadProfileImage,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getImage() == null));

		chatTemplates.add(new ChatTemplate(Text.engagement_uploadProfileAttributes,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getAttr0() == null && contact.getAttr1() == null && contact.getAttr2() == null
						&& contact.getAttr3() == null && contact.getAttr4() == null && contact.getAttr5() == null
						&& contact.getAttr() == null));

		chatTemplates.add(new ChatTemplate(Text.engagement_allowLocation,
				"",
				contact -> contact.getLongitude() == null && contact.getOs() != OS.web));

		chatTemplates.add(new ChatTemplate(Text.engagement_patience,
				"pageInfo.socialShare()",
				contact -> contact.getLongitude() != null));

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

	public String sendSpontifyEmail() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 17)
			return "";
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
			return list.size() + " spontify emails sent";
		}
		return "no spontify email sent\n";
	}

	public String sendRegistrationReminder() throws Exception {
		final GregorianCalendar gc = new GregorianCalendar();
		if (gc.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY || gc.get(Calendar.HOUR_OF_DAY) != 19)
			return "";
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
			return list.size() + " registration reminder emails sent";
		}
		return "no registration reminder email sent\n";
	}

	public String sendChats() throws Exception {
		resetChatInstallCurrentVersion();
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch("contact.id<>" + adminId + " and contact.verified=true and contact.version is not null");
		final Result ids = repository.list(params);
		params.setQuery(Query.contact_chat);
		int count = 0;
		String temp = "";
		for (int i = 0; i < ids.size(); i++) {
			if (!adminBlocked(ids.get(i).get("contact.id"))) {
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + ids.get(i).get("contact.id")
						+ " and chat.createdAt>'"
						+ Instant.now().minus(Duration.ofDays(7 + (int) (Math.random() * 4)))
								.minus(Duration.ofHours((int) (Math.random() * 12))).toString()
						+ '\'');
				if (repository.list(params).size() == 0) {
					temp += "," + ids.get(i).get("contact.id");
					if (sendChatTemplate(repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")),
							params))
						count++;
				}
			}
		}
		return count + "/" + ids.size() + " chats sent\n" + temp.replaceFirst(",", "") + "\n";
	}

	private boolean adminBlocked(Object id) {
		paramsAdminBlocked.setSearch("contactBlock.contactId=" + adminId + " and contactBlock.contactId2=" + id
				+ " or contactBlock.contactId=" + id + " and contactBlock.contactId2=" + id);
		return repository.list(paramsAdminBlocked).size() > 0;
	}

	private void resetChatInstallCurrentVersion() throws Exception {
		if (currentVersion == null)
			currentVersion = (String) repository.one(new QueryParams(Query.contact_maxAppVersion)).get("c");
		final QueryParams params = new QueryParams(Query.contact_listChatFlat);
		params.setLimit(0);
		params.setSearch("chat.textId='" + Text.engagement_installCurrentVersion.name() +
				"' and contact.version='" + currentVersion + "'");
		final Result ids = repository.list(params);
		for (int i = 0; i < ids.size(); i++) {
			final Chat chat = repository.one(Chat.class, (BigInteger) ids.get(i));
			chat.setTextId(null);
			repository.save(chat);
		}
	}

	public String sendNearBy() throws Exception {
		final QueryParams params = new QueryParams(Query.contact_listId);
		params.setSearch(
				"contact.verified=true and contact.version is not null and contact.longitude is not null and (" +
						"length(contact.attrInterest)>0 or length(contact.attrInterestEx)>0 or " +
						"length(contact.attr0)>0 or length(contact.attr0Ex)>0 or " +
						"length(contact.attr1)>0 or length(contact.attr1Ex)>0 or " +
						"length(contact.attr2)>0 or length(contact.attr2Ex)>0 or " +
						"length(contact.attr3)>0 or length(contact.attr3Ex)>0 or " +
						"length(contact.attr4)>0 or length(contact.attr4Ex)>0 or " +
						"length(contact.attr5)>0 or length(contact.attr5Ex)>0)");
		final Result ids = repository.list(params);
		params.setQuery(Query.contact_chat);
		int count = 0;
		String temp = "";
		for (int i = 0; i < ids.size(); i++) {
			if (!adminBlocked(ids.get(i).get("contact.id"))) {
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + ids.get(i).get("contact.id")
						+ " and chat.textId like '"
						+ Text.engagement_nearByLocation.name()
								.substring(0, Text.engagement_nearByLocation.name().indexOf('L'))
						+ "%' and chat.createdAt>'"
						+ Instant.now().minus(Duration.ofDays(4))
								.minus(Duration.ofHours((int) (Math.random() * 12))).toString()
						+ '\'');
				if (repository.list(params).size() == 0) {
					final Contact contact = repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
					temp += "," + contact.getId();
					if (sendEvent(contact) || sendLocation(contact) || sendContact(contact))
						count++;
				}
			}
		}
		return count + "/" + ids.size() + " near by chats sent\n" + temp.replaceFirst(",", "") + "\n";
	}

	private boolean sendChatTemplate(Contact contact, QueryParams params) throws Exception {
		final int hour = Instant.now().minus(Duration.ofMinutes(
				contact.getTimezoneOffset() == null ? -60 : contact.getTimezoneOffset().longValue()))
				.atZone(ZoneOffset.UTC).getHour();
		if (hour > 6 && hour < 22) {
			for (ChatTemplate chatTemplate : chatTemplates) {
				if (chatTemplate.eligible(contact)) {
					params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
							+ " and chat.textId='" + chatTemplate.textId.name() + '\'');
					if (repository.list(params).size() == 0) {
						sendChat(chatTemplate.textId, contact, null, chatTemplate.action);
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean sendEvent(Contact contact) throws Exception {
		final QueryParams params = new QueryParams(Query.event_listCurrent);
		params.setUser(contact);
		params.setLatitude(contact.getLatitude());
		params.setLongitude(contact.getLongitude());
		final Result result = repository.list(params);
		params.setQuery(Query.contact_chat);
		params.setLatitude(null);
		for (int i = 0; i < result.size(); i++) {
			final Event event = repository.one(Event.class, (BigInteger) result.get(0).get("event.id"));
			params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
					+ " and chat.textId='" + Text.engagement_nearByLocation.name() + "' and chat.action like '%"
					+ Strings.encodeParam("e=" + event.getId()) + "%'");
			if (repository.list(params).size() == 0) {
				if (getScoreContact(contact, repository.one(Contact.class, event.getContactId())) > 0.8) {
					sendChat(Text.engagement_nearByEvent, contact,
							repository.one(Location.class, event.getLocationId()),
							"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("e=" + event.getId())
									+ "&quot;,event)");
					return true;
				}
			}
		}
		return false;
	}

	private boolean sendLocation(Contact contact) throws Exception {
		final QueryParams params = new QueryParams(Query.location_list);
		params.setLatitude(contact.getLatitude());
		params.setLongitude(contact.getLongitude());
		params.setUser(contact);
		String search = "";
		for (int i = 0; i < 6; i++) {
			String attr = (String) contact.getClass().getMethod("getAttr" + i).invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(location.attr" + i + ", '" + attr.replace('\u0015', '|') + "')=1 or ";
			attr = (String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(location.attr" + i + "Ex, '" + attr.replace(',', '|') + "')=1 or ";
		}
		if (search.endsWith(" or ")) {
			params.setSearch("locationFavorite.id is null and location.image is not null and ("
					+ search.substring(0, search.length() - 4) + ")");
			final Result result = repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			double score = 0;
			Location location = null;
			for (int i = 0; i < result.size(); i++) {
				final Location l = repository.one(Location.class, (BigInteger) result.get(i).get("location.id"));
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
						+ " and chat.textId='" + Text.engagement_nearByLocation.name() + "' and chat.action like '%"
						+ Strings.encodeParam("l=" + l.getId()) + "%'");
				if (repository.list(params).size() == 0) {
					double s = getScoreLocation(contact, l);
					if (s > score) {
						score = s;
						location = l;
					}
				}
			}
			if (location != null) {
				sendChat(Text.engagement_nearByLocation, contact, location,
						"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("l=" + location.getId())
								+ "&quot;,event)");
				return true;
			}
		}
		return false;
	}

	private boolean sendContact(Contact contact) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_list);
		params.setLatitude(contact.getLatitude());
		params.setLongitude(contact.getLongitude());
		params.setUser(contact);
		String search = "";
		if (!Strings.isEmpty(contact.getBudget()))
			search += "REGEXP_LIKE(contact.budget, '" + contact.getBudget().replace('\u0015', '|') + "')=1) and (";
		if (!Strings.isEmpty(contact.getAgeMale()))
			search += "contact.gender=1 and contact.age>=" + contact.getAgeMale().split(",")[0] + " and contact.age<="
					+ contact.getAgeMale().split(",")[1] + " or ";
		if (!Strings.isEmpty(contact.getAgeFemale()))
			search += "contact.gender=2 and contact.age>=" + contact.getAgeFemale().split(",")[0] + " and contact.age<="
					+ contact.getAgeFemale().split(",")[1] + " or ";
		if (!Strings.isEmpty(contact.getAgeDivers()))
			search += "contact.gender=3 and contact.age>=" + contact.getAgeDivers().split(",")[0] + " and contact.age<="
					+ contact.getAgeDivers().split(",")[1] + " or ";
		if (search.contains("contact.age"))
			search = search.substring(0, search.length() - 4) + ") and (";
		for (int i = 0; i < 6; i++) {
			String attr = (String) contact.getClass().getMethod("getAttr" + i).invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(contact.attr" + i + ", '" + attr.replace('\u0015', '|') + "')=1 or ";
			attr = (String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact);
			if (!Strings.isEmpty(attr))
				search += "REGEXP_LIKE(contact.attr" + i + "Ex, '" + attr.replace(',', '|') + "')=1 or ";
		}
		if (!Strings.isEmpty(contact.getAttrInterest()))
			search += "REGEXP_LIKE(contact.attr, '" + contact.getAttrInterest().replace('\u0015', '|') + "')=1 or ";
		if (!Strings.isEmpty(contact.getAttrInterestEx()))
			search += "REGEXP_LIKE(contact.attrEx, '" + contact.getAttrInterestEx().replace(',', '|') + "')=1 or ";
		if (search.endsWith(" or ")) {
			params.setSearch("contactLink.id is null and contact.image is not null and ("
					+ search.substring(0, search.length() - 4) + ")");
			final Result result = repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			for (int i = 0; i < result.size(); i++) {
				final Contact contact2 = repository.one(Contact.class, (BigInteger) result.get(i).get("contact.id"));
				params.setSearch("chat.contactId=" + adminId + " and chat.contactId2=" + contact.getId()
						+ " and chat.textId='" + Text.engagement_nearByContact.name() + "' and chat.action like '%"
						+ Strings.encodeParam("l=" + contact2.getId()) + "%'");
				if (repository.list(params).size() == 0) {
					if (getScoreContact(contact, contact2) > 0.8) {
						final Location location = new Location();
						location.setName(contact2.getPseudonym());
						sendChat(Text.engagement_nearByContact, contact, location,
								"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("p=" + contact.getId())
										+ "&quot;,event)");
						return true;
					}
				}
			}
		}
		return false;
	}

	private double getScoreContact(Contact contact, Contact contact2) throws Exception {
		final Score score = new Score();
		for (int i = 0; i < 6; i++) {
			match((String) contact.getClass().getMethod("getAttr" + i).invoke(contact),
					(String) contact2.getClass().getMethod("getAttr" + i).invoke(contact2), score);
			match((String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact),
					(String) contact2.getClass().getMethod("getAttr" + i + "Ex").invoke(contact2), score);
		}
		match(contact.getAttrInterest(), contact2.getAttr(), score);
		match(contact.getAttrInterestEx(), contact2.getAttrEx(), score);
		match(contact.getBudget(), contact2.getBudget(), score);
		return score.total < 8 ? 0 : score.percantage();
	}

	private double getScoreLocation(Contact contact, Location location) throws Exception {
		final Score score = new Score();
		match(location.getBudget(), contact.getBudget(), score);
		if (score.match > 0) {
			for (int i = 0; i < 6; i++) {
				match((String) location.getClass().getMethod("getAttr" + i).invoke(location),
						(String) contact.getClass().getMethod("getAttr" + i).invoke(contact), score);
				match((String) location.getClass().getMethod("getAttr" + i + "Ex").invoke(location),
						(String) contact.getClass().getMethod("getAttr" + i + "Ex").invoke(contact), score);
			}
		}
		return score.total < 2 ? 0 : score.percantage();
	}

	private class Score {
		private double total = 0;
		private double match = 0;

		private double percantage() {
			return total == 0 ? 0 : match / total;
		}
	}

	private void match(String attributes, String attributesCompare, Score score) {
		if (!Strings.isEmpty(attributes)) {
			final String[] attr = attributes.split(attributes.contains("\u0015") ? "\u0015" : ",");
			score.total += attr.length;
			if (attributesCompare != null) {
				for (int i2 = 0; i2 < attr.length; i2++) {
					if (attributesCompare.contains(attr[i2]))
						score.match++;
				}
			}
		}
	}

	private void sendChat(Text textId, Contact contact, Location location, String action) throws Exception {
		String s = textId.getText(contact.getLanguage());
		for (REPLACMENT rep : REPLACMENT.values())
			s = rep.replace(s, contact, location);
		final Chat chat = new Chat();
		chat.setContactId(adminId);
		chat.setContactId2(contact.getId());
		chat.setSeen(false);
		chat.setAction(action);
		chat.setTextId(textId.name());
		chat.setNote(s);
		repository.save(chat);
	}
}