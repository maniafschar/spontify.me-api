package com.jq.findapp.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Ai;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.OS;
import com.jq.findapp.entity.ContactChat;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Group;
import com.jq.findapp.service.model.Match;
import com.jq.findapp.util.Score;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class EngagementService {
	@Autowired
	private Repository repository;

	@Autowired
	private AuthenticationService authenticationService;

	@Autowired
	private AiService aiService;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private MatchDayService matchDayService;

	@Autowired
	private Text text;

	private static Map<Integer, String[]> QUESTIONS = new HashMap<>();
	static {
		QUESTIONS.put(4,
				new String[] {
						"Fasse in etwa 300 Wörtern die Highlights aus den Begegnungen des {teamHome} gegen {teamAway} zusammen.",
						"Liste Top 10 Begegnungen der Kontrahenten {teamHome} gegen {teamAway} auf.",
						"Welche besonderen Anekdoten gibt es zu den Spielen zwischen {teamHome} und {teamAway}?",
						"Welche Spieler prägten die Partien zwischen {teamHome} und {teamAway}?"
				});
	}

	private static Map<BigInteger, String> currentVersion = new HashMap<>();

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
			return this.condition == null ? true : this.condition.isTrue(contact);
		}
	}

	private final List<ChatTemplate> chatTemplates = new ArrayList<>();

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
				return externalService.getAddress(contact.getLatitude(), contact.getLongitude(), false).getTown();
			} catch (final Exception e) {
				throw new IllegalArgumentException("no town", e);
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
					"contact.createdAt>=cast('" + Instant.ofEpochMilli(contact.getModifiedAt().getTime())
							+ "' as timestamp)");
			return "" + repository.list(params).size();
		}),
		NEW_CONTACTS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"contact.createdAt>=cast('" + Instant.ofEpochMilli(contact.getModifiedAt().getTime())
							+ "' as timestamp)");
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
					"location.createdAt>=cast('" + Instant.ofEpochMilli(contact.getModifiedAt().getTime())
							+ "' as timestamp)");
			return "" + repository.list(params).size();
		}),
		NEW_LOCATIONS_DISTANCE((contact, location, externalService, repository) -> {
			final QueryParams params = new QueryParams(Query.location_list);
			params.setUser(contact);
			params.setLatitude(contact.getLatitude());
			params.setLongitude(contact.getLongitude());
			params.setSearch(
					"location.createdAt>=cast('" + Instant.ofEpochMilli(contact.getModifiedAt().getTime())
							+ "' as timestamp)");
			final Result result = repository.list(params);
			return ""
					+ (int) (((Number) result.get(result.size() - 1).get("_geolocationDistance")).doubleValue() + 0.5);
		}),
		VERSION((contact, location, externalService, repository) -> currentVersion.get(contact.getClientId()));

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
			return s.contains(this.name())
					? s.replaceAll(this.name(), this.exec.replace(contact, location, externalService, repository))
					: s;
		}

	}

	public EngagementService() {
		this.chatTemplates.add(new ChatTemplate(TextId.engagement_uploadProfileImage,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> contact.getImage() == null));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_uploadProfileAttributes,
				"ui.navigation.goTo(&quot;settings&quot;)",
				contact -> (Strings.isEmpty(contact.getSkills()) && Strings.isEmpty(contact.getSkillsText()))
						|| (Strings.isEmpty(contact.getAgeMale()) && Strings.isEmpty(contact.getAgeFemale())
								&& Strings.isEmpty(contact.getAgeDivers()))
						|| contact.getGender() == null || contact.getBirthday() == null));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_newTown,
				"pageInfo.socialShare()",
				contact -> contact.getLatitude() != null
						&& contact.getModifiedAt() != null
						&& contact.getModifiedAt()
								.after(new Date(Instant.now().minus(Duration.ofDays(1)).toEpochMilli()))));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_allowLocation,
				"",
				contact -> contact.getLongitude() == null && contact.getOs() != OS.web));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_addEvent,
				"",
				contact -> {
					final QueryParams params = new QueryParams(Query.event_list);
					params.setUser(contact);
					params.setSearch("event.contactId=" + contact.getId());
					if (this.repository.list(params).size() == 0) {
						params.setQuery(Query.location_listFavorite);
						params.setSearch("locationFavorite.contactId=" + contact.getId());
						return this.repository.list(params).size() > 0;
					}
					return false;
				}));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_newLocations,
				"",
				contact -> {
					if (contact.getLongitude() == null)
						return false;
					final QueryParams params = new QueryParams(Query.location_listId);
					params.setUser(contact);
					params.setDistance(20);
					params.setLatitude(contact.getLatitude());
					params.setLongitude(contact.getLongitude());
					params.setSearch(
							"location.createdAt>=cast('"
									+ Instant.ofEpochMilli(contact.getModifiedAt().getTime())
									+ "' as timestamp)");
					return this.repository.list(params).size() > 1;
				}));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_newContacts,
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
							"contact.createdAt>=cast('"
									+ Instant.ofEpochMilli(contact.getModifiedAt().getTime()) + "' as timestamp)");
					return this.repository.list(params).size() > 9;
				}));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_addFriends,
				"pageInfo.socialShare()",
				null));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_installCurrentVersion,
				"global.openStore()",
				contact -> !Strings.isEmpty(contact.getVersion()) && contact.getOs() != OS.web
						&& currentVersion.get(contact.getClientId()) != null
						&& currentVersion.get(contact.getClientId()).compareTo(contact.getVersion()) > 0));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_praise,
				"",
				contact -> contact.getLongitude() != null && contact.getImage() != null
						&& contact.getBirthday() != null && !Strings.isEmpty(contact.getDescription())
						&& !Strings.isEmpty(contact.getSkills())
						&& (!Strings.isEmpty(contact.getAgeDivers()) || !Strings.isEmpty(contact.getAgeFemale())
								|| !Strings.isEmpty(contact.getAgeMale()))));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_patience,
				"pageInfo.socialShare()",
				contact -> contact.getLongitude() != null));

		this.chatTemplates.add(new ChatTemplate(TextId.engagement_like, null, null));
	}

	@Cron("40 10")
	public CronResult cronRegistration() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.createdAt<cast('" + Instant.now().minus(Duration.ofHours(3))
					+ "' as timestamp) and contact.verified=false");
			params.setLimit(0);
			final Result list = this.repository.list(params);
			int count = 0;
			params.setQuery(Query.misc_listTicket);
			for (int i = 0; i < list.size(); i++) {
				final Contact to = this.repository.one(Contact.class, (BigInteger) list.get(i).get("contact.id"));
				params.setSearch("ticket.type='EMAIL' and ticket.subject='" + to.getEmail() + "'");
				final Result emails = this.repository.list(params);
				if (this.timeToSendNewRegistrationReminder(
						emails.size() == 0 ? null : (Timestamp) emails.get(0).get("ticket.createdAt"), to)) {
					this.authenticationService.recoverSendEmailReminder(to);
					count++;
				}
			}
			result.body = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	boolean timeToSendNewRegistrationReminder(final Timestamp lastEmail, final Contact contact) {
		if (lastEmail == null)
			return true;
		final List<Integer> weeks = Arrays.asList(1, 4, 12, 26);
		for (int i = weeks.size() - 1; i >= 0; i--) {
			final int w = weeks.get(i);
			final int days = weeks.stream().filter(e -> e <= w).mapToInt(e -> e).sum() * 7;
			final Instant createdAtPlusDays = Instant.ofEpochMilli(contact.getCreatedAt().getTime())
					.plus(Duration.ofDays(days));
			if (createdAtPlusDays.isBefore(Instant.now()))
				return Instant.ofEpochMilli(lastEmail.getTime()).isBefore(createdAtPlusDays);
		}
		return false;
	}

	@Cron(group = Group.Four)
	public CronResult cron() {
		final CronResult result = new CronResult();
		try {
			this.resetChatInstallCurrentVersion();
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setLimit(0);
			params.setSearch("contact.verified=true and contact.version is not null");
			final Result ids = this.repository.list(params);
			params.setQuery(Query.contact_chat);
			int count = 0;
			long t = System.currentTimeMillis();
			for (int i = 0; i < ids.size(); i++) {
				final BigInteger contactId = (BigInteger) ids.get(i).get("contact.id");
				final BigInteger adminId = this.repository
						.one(Client.class, this.repository.one(Contact.class, contactId).getClientId())
						.getAdminId();
				params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contactId);
				if (this.repository.list(params).size() == 0) {
					this.sendChat(TextId.engagement_welcome,
							this.repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id")), null, null);
					count++;
				}
			}
			result.body += "welcome: " + count + ", time: " + (System.currentTimeMillis() - t);
			count = 0;
			t = System.currentTimeMillis();
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = this.repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (this.timeForNewChat(contact, params, false) && this.sendChatTemplate(contact))
					count++;
			}
			result.body += "\ntemplate: " + count + ", time: " + (System.currentTimeMillis() - t);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Cron(value = "0 8", group = Group.Four)
	public CronResult cronAi() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.clientId=4 and contact.skills like '%9.%'");
			final Result contacts = this.repository.list(params);
			final Map<Integer, Ai> text = new HashMap<>();
			for (int i = 0; i < contacts.size(); i++) {
				final Contact contact = this.repository.one(Contact.class,
						(BigInteger) contacts.get(i).get("contact.id"));
				final List<Integer> teamIds = Arrays.asList(contact.getSkills().split("\\|"))
						.stream().filter(e -> e.startsWith("9.")).map(e -> Integer.valueOf(e.substring(2)))
						.collect(Collectors.toList());
				for (int i2 = 0; i2 < teamIds.size(); i2++) {
					if (!text.containsKey(teamIds.get(i2))) {
						final Match match = this.matchDayService.retrieveNextMatch(teamIds.get(i2));
						text.put(teamIds.get(i2),
								match == null ? null
										: this.aiService
												.text(QUESTIONS.get(4)[(int) (Math.random() * QUESTIONS.get(4).length)]
														.replace("{teamHome}", match.teams.home.name)
														.replace("{teamAway}", match.teams.away.name)));
					}
					if (text.get(teamIds.get(i2)) != null) {
						final ContactChat chat = new ContactChat();
						chat.setContactId(this.repository.one(Client.class, contact.getClientId()).getAdminId());
						chat.setContactId2(contact.getId());
						chat.setAction("ai:" + text.get(teamIds.get(i2)).getId());
						chat.setNote(text.get(teamIds.get(i2)).getAnswer());
						this.repository.save(chat);
					}
				}
			}
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private boolean timeForNewChat(final Contact contact, final QueryParams params, final boolean nearBy) {
		final int hour = Instant.now().atZone(TimeZone.getTimeZone(contact.getTimezone()).toZoneId()).getHour();
		if (hour > 6 && hour < 22) {
			final BigInteger adminId = this.repository.one(Client.class, contact.getClientId()).getAdminId();
			params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contact.getId()
					+ " and contactChat.createdAt>cast('"
					+ Instant.now().minus(Duration.ofDays(32)).toString()
					+ "' as timestamp)");
			if (this.repository.list(params).size() == 0) {
				params.setSearch("contactChat.textId is not null and contactChat.contactId=" + adminId
						+ " and contactChat.contactId2=" + contact.getId());
				final Result lastChats = this.repository.list(params);
				if (lastChats.size() == 0)
					return true;
				final boolean isLastChatNearBy = ((TextId) lastChats.get(0).get("contactChat.textId")).name()
						.startsWith(TextId.engagement_nearByLocation.name().substring(0,
								TextId.engagement_nearByLocation.name().indexOf('L')));
				if (!nearBy && isLastChatNearBy || nearBy && !isLastChatNearBy)
					return true;
				if (((Timestamp) lastChats.get(0).get("contactChat.createdAt"))
						.before(new Timestamp(Instant.now().minus(Duration.ofDays(7)).toEpochMilli())))
					return true;
			}
		}
		return false;
	}

	private void resetChatInstallCurrentVersion() throws Exception {
		if (currentVersion.size() == 0) {
			final QueryParams params = new QueryParams(Query.contact_maxAppVersion);
			params.setSearch("contact.email not like '%@jq-consulting.de' and contact.id<>client.adminId");
			final Result result = this.repository.list(params);
			for (int i = 0; i < result.size(); i++)
				currentVersion.put((BigInteger) result.get(i).get("contact.clientId"),
						(String) result.get(i).get("_c"));
		}
		final Iterator<BigInteger> it = currentVersion.keySet().iterator();
		while (it.hasNext()) {
			final BigInteger clientId = it.next();
			final QueryParams params = new QueryParams(Query.contact_listChatFlat);
			params.setLimit(0);
			params.setSearch("cast(contactChat.textId as text)='" + TextId.engagement_installCurrentVersion.name() +
					"' and contact.version='" + currentVersion.get(clientId) + "' and contact.clientId=" + clientId);
			final Result ids = this.repository.list(params);
			for (int i = 0; i < ids.size(); i++) {
				final ContactChat contactChat = this.repository.one(ContactChat.class,
						(BigInteger) ids.get(i).get("contactChat.id"));
				contactChat.setTextId(null);
				this.repository.save(contactChat);
			}
		}
	}

	@Cron(group = Group.Three)
	public CronResult cronNearBy() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.verified=true and contact.notification like '%" +
					NotificationService.NotificationType.engagement + "%' and "
					+ "contact.version is not null and contact.longitude is not null and "
					+ "(length(contact.skills)>0 or length(contact.skillsText)>0)");
			params.setLimit(0);
			final Result ids = this.repository.list(params);
			params.setQuery(Query.contact_chat);
			int count = 0;
			for (int i = 0; i < ids.size(); i++) {
				final Contact contact = this.repository.one(Contact.class, (BigInteger) ids.get(i).get("contact.id"));
				if (!contact.getId().equals(this.repository.one(Client.class, contact.getClientId()).getAdminId())
						&& this.timeForNewChat(contact, params, true)) {
					final String action = this.getLastNearByAction(params, (BigInteger) ids.get(i).get("contact.id"));
					if (!action.startsWith("p=") && this.sendContact(contact))
						count++;
				}
			}
			result.body = "" + count;
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private String getLastNearByAction(final QueryParams params, final BigInteger id) {
		params.setSearch("contactChat.action is not null and cast(contactChat.textId as text) like '"
				+ TextId.engagement_nearByLocation.name().substring(0,
						TextId.engagement_nearByLocation.name().indexOf('L'))
				+ "%' and contactChat.contactId="
				+ this.repository.one(Client.class, this.repository.one(Contact.class, id).getClientId()).getAdminId()
				+ " and contactChat.contactId2=" + id);
		final Result chats = this.repository.list(params);
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
		for (final ChatTemplate chatTemplate : this.chatTemplates) {
			if (chatTemplate.eligible(contact) &&
					this.sendChat(chatTemplate.textId, contact, null, chatTemplate.action))
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
			final Result result = this.repository.list(params);
			params.setQuery(Query.contact_chat);
			params.setLatitude(null);
			double score = 0;
			Contact contact2 = null;
			for (int i = 0; i < result.size(); i++) {
				final Contact c = this.repository.one(Contact.class, (BigInteger) result.get(i).get("contact.id"));
				params.setSearch(
						"contactChat.contactId=" + this.repository.one(Client.class, contact.getClientId()).getAdminId()
								+ " and contactChat.contactId2=" + contact.getId()
								+ " and cast(contactChat.textId as text)='" + TextId.engagement_nearByContact.name()
								+ "' and contactChat.action like '%"
								+ Strings.encodeParam("p=" + c.getId()) + "%'");
				if (this.repository.list(params).size() == 0) {
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
				this.sendChat(TextId.engagement_nearByContact, contact, location,
						"ui.navigation.autoOpen(&quot;" + Strings.encodeParam("p=" + contact2.getId())
								+ "&quot;,event)");
				return true;
			}
		}
		return false;
	}

	public boolean sendChat(final TextId textId, final Contact contact, final Location location, final String action)
			throws Exception {
		final BigInteger adminId = this.repository.one(Client.class, contact.getClientId()).getAdminId();
		if (contact.getId().equals(adminId))
			return false;
		final QueryParams params = new QueryParams(Query.contact_chat);
		params.setSearch("contactChat.contactId=" + adminId + " and contactChat.contactId2=" + contact.getId()
				+ " and cast(contactChat.textId as text)='" + textId.name() + '\'');
		if (this.repository.list(params).size() == 0) {
			String s = this.text.getText(contact, textId);
			try {
				for (final REPLACMENT rep : REPLACMENT.values())
					s = rep.replace(s, contact, location, this.externalService, this.repository);
				final ContactChat contactChat = new ContactChat();
				contactChat.setContactId(adminId);
				contactChat.setContactId2(contact.getId());
				contactChat.setSeen(false);
				contactChat.setAction(action);
				contactChat.setTextId(textId);
				contactChat.setNote(s);
				this.repository.save(contactChat);
				return true;
			} catch (final IllegalArgumentException ex) {
				if (!"duplicate chat".equals(ex.getMessage()) && !"no town".equals(ex.getMessage()))
					throw new RuntimeException(ex);
			}
		}
		return false;
	}
}
