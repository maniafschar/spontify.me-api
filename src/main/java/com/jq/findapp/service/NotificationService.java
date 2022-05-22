package com.jq.findapp.service;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.NotFoundException;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification;
import com.jq.findapp.entity.ContactVisit;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;

@Service
public class NotificationService {
	@Autowired
	private Repository repository;

	@Autowired
	private JavaMailSender email;

	@Autowired
	private Android android;

	@Autowired
	private Ios ios;

	@Value("${app.url}")
	private String server;

	@Value("${app.email.address}")
	private String from;

	enum NotificationIDType {
		Email, Device, EmailOrDevice, EmailAndDevice
	}

	public enum NotificationID {
		accountDelete(NotificationIDType.EmailOrDevice, true), //
		birthday(NotificationIDType.EmailOrDevice, true), //
		chatLocation(NotificationIDType.EmailOrDevice, true),
		feedback(NotificationIDType.Email, false), //
		friendAppro(NotificationIDType.EmailOrDevice, true), //
		friendReq(NotificationIDType.EmailOrDevice, true), //
		markEvent(NotificationIDType.EmailOrDevice, true), //
		newMsg(NotificationIDType.EmailOrDevice, false), //
		pwReset(NotificationIDType.Email, false), //
		ratingLocMat(NotificationIDType.EmailOrDevice, true), //
		ratingProfile(NotificationIDType.EmailOrDevice, true), //
		visitLocation(NotificationIDType.EmailOrDevice, true), //
		visitProfile(NotificationIDType.EmailOrDevice, true), //
		welcomeExt(NotificationIDType.Email, false), //
		wtd(NotificationIDType.EmailOrDevice, true), //
		findMe(NotificationIDType.EmailOrDevice, true), //
		locMarketing(NotificationIDType.EmailOrDevice, true), //
		mgMarketing(NotificationIDType.EmailOrDevice, true);

		private final NotificationIDType type;
		private final boolean save;

		private NotificationID(NotificationIDType type, boolean save) {
			this.type = type;
			this.save = save;
		}

		public NotificationIDType getType() {
			return type;
		}

		public boolean isSave() {
			return save;
		}
	}

	@Async
	public void contactSaveVisitAndNotifyOnMatch(final Contact user, final BigInteger contactId2)
			throws Exception {
		if (!contactId2.equals(user.getId())) {
			final QueryParams params2 = new QueryParams(Query.contact_listVisit);
			params2.setUser(user);
			params2.setSearch("contactVisit.contactId=" + user.getId() + " and contactVisit.contactId2="
					+ contactId2 + " and contact.id=" + contactId2);
			final Map<String, Object> visitMap = repository.one(params2);
			final ContactVisit visit;
			if (visitMap == null) {
				visit = new ContactVisit();
				visit.setContactId(user.getId());
				visit.setContactId2(contactId2);
				visit.setCount(1L);
			} else {
				visit = repository.one(ContactVisit.class, (BigInteger) visitMap.get("contactVisit.id"));
				visit.setCount(visit.getCount() + 1);
			}
			repository.save(visit);
			sendNotificationOnMatch(NotificationID.visitProfile, user, repository.one(Contact.class, contactId2));
		}
	}

	@Async
	public void locationNotifyOnMatch(final Contact me, final BigInteger locationId,
			final NotificationID notificationID, String param) throws Exception {
		final QueryParams params2 = new QueryParams(Query.location_listFavorite);
		params2.setSearch("locationFavorite.locationId=" + locationId);
		final Result favorites = repository.list(params2);
		final Location location = repository.one(Location.class, locationId);
		for (int i = 0; i < favorites.size(); i++)
			sendNotificationOnMatch(notificationID,
					me,
					repository.one(Contact.class, (BigInteger) favorites.get(i).get("locationFavorite.contactId")),
					location.getName(), param);
	}

	@Async
	public String[] sendNotificationOnMatch(NotificationID textID, final Contact me, final Contact other,
			String... param)
			throws Exception {
		if (!me.getId().equals(other.getId()) && me.getAge() != null && me.getGender() != null) {
			final String s2 = me.getGender().intValue() == 1 ? other.getAgeMale()
					: me.getGender().intValue() == 2 ? other.getAgeFemale()
							: other.getAgeDivers();
			if (!Strings.isEmpty(s2)) {
				final String[] s = s2.split(",");
				final int age = me.getAge().intValue();
				if (s2.indexOf(',') < 1 || ("18".equals(s[0]) || age >= Integer.parseInt(s[0]))
						&& ("99".equals(s[1]) || age <= Integer.parseInt(s[1]))) {
					final List<String> attribs = compareAttributes(me.getAttr(), other.getAttrInterest());
					final List<String> attribsEx = compareAttributes(me.getAttrEx(), other.getAttrInterestEx());
					if (attribs.size() > 0 || attribsEx.size() > 0) {
						String[] param2 = new String[param == null ? 1 : param.length + 1];
						param2[0] = "" + (attribs.size() + attribsEx.size());
						if (param != null) {
							for (int i = 0; i < param.length; i++)
								param2[i + 1] = param[i];
						}
						if (sendNotificationInternal(me, other, textID, Strings.encodeParam("p=" + me.getId()), param2))
							return new String[] { attributesToString(attribs), attributesToString(attribsEx) };
					}
				}
			}
		}
		return null;
	}

	@Async
	public void sendNotification(Contact contactFrom, Contact contactTo, NotificationID notificationID,
			String action, String... param) throws Exception {
		sendNotificationInternal(contactFrom, contactTo, notificationID, action, param);
	}

	private boolean sendNotificationInternal(Contact contactFrom, Contact contactTo, NotificationID notificationID,
			String action, String... param) throws Exception {
		if (!contactTo.getVerified() && notificationID != NotificationID.welcomeExt
				&& notificationID != NotificationID.pwReset)
			return false;
		QueryParams params = new QueryParams(Query.contact_block);
		params.setUser(contactFrom);
		params.setSearch("contactBlock.contactId=" + contactFrom.getId() + " and contactBlock.contactId2="
				+ contactTo.getId() + " or contactBlock.contactId="
				+ contactTo.getId() + " and contactBlock.contactId2=" + contactFrom.getId());
		if (repository.list(params).size() > 0)
			return false;
		final StringBuilder text = new StringBuilder(
				Text.valueOf("mail_" + notificationID).getText(contactTo.getLanguage()));
		if (param != null) {
			for (int i = 0; i < param.length; i++)
				Strings.replaceString(text, "<jq:EXTRA_" + (i + 1) + "/>", param[i]);
		}
		if (text.length() > 250) {
			text.delete(247, text.length());
			if (text.lastIndexOf(" ") > 180)
				text.delete(text.lastIndexOf(" "), text.length());
			text.append("...");
		}
		Object notID = null;
		if (notificationID.isSave()) {
			String s = text.toString();
			if (s.charAt(1) == ' ' && (s.charAt(0) == ',' || s.charAt(0) == ':'))
				s = s.substring(1);
			params.setQuery(Query.contact_notification);
			params.setSearch("contactNotification.contactId=" + contactTo.getId() +
					" and contactNotification.contactId2=" + contactFrom.getId() +
					" and contactNotification.action='" + action
					+ "' and contactNotification.textId='" + notificationID.name()
					+ "' and TO_DAYS(contactNotification.createdAt) + 6>TO_DAYS(current_timestamp)");
			if (repository.list(params).size() > 0)
				return false;
			final ContactNotification notification = new ContactNotification();
			notification.setAction(action);
			notification.setContactId(contactTo.getId());
			notification.setContactId2(contactFrom.getId());
			notification.setText(s);
			notification.setTextId(notificationID.name());
			repository.save(notification);
			notID = notification.getId();
		}
		if (userWantsNotification(notificationID, contactTo)) {
			if (contactFrom.getId().longValue() != contactTo.getId().longValue()
					&& (text.charAt(0) < 'A' || text.charAt(0) > 'Z'))
				text.insert(0, contactFrom.getPseudonym() + (text.charAt(0) == ':' ? "" : " "));
			boolean b = notificationID.getType() != NotificationIDType.Email
					&& !Strings.isEmpty(contactTo.getPushSystem()) &&
					!Strings.isEmpty(contactTo.getPushToken());
			if (b)
				b = sendNotificationDevice(text, contactTo, action);
			if (!b || notificationID.getType() == NotificationIDType.EmailAndDevice)
				sendNotificationEmail(contactFrom, contactTo, text,
						action == null ? (notID == null ? null : "n=" + notID) : action);
			return true;
		}
		return false;
	}

	public Ping getPingValues(Contact contact) {
		final QueryParams params = new QueryParams(Query.contact_pingVisit);
		params.setUser(contact);
		final Ping values = new Ping();
		values.userId = contact.getId();
		values.visit = ((Number) repository.one(params).get("_c")).intValue();
		params.setQuery(Query.contact_pingFriendRequest);
		values.friendRequest = ((Number) repository.one(params).get("_c")).intValue();
		params.setQuery(Query.contact_pingNotification);
		values.notification = ((Number) repository.one(params).get("_c")).intValue();
		values.totalNew = values.notification;
		params.setQuery(Query.contact_pingChatNew);
		params.setLimit(0);
		Result list = repository.list(params);
		values.chatNew = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			values.chatNew.put("" + row.get("chat.contactId"), ((Number) row.get("_c")).intValue());
			values.totalNew += ((Number) values.chatNew.get("" + row.get("chat.contactId"))).intValue();
		}
		params.setQuery(Query.contact_pingChatUnseen);
		list = repository.list(params);
		values.chatUnseen = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			values.chatUnseen.put("" + row.get("chat.contactId2"), ((Number) row.get("_c")).intValue());
		}
		return values;
	}

	private boolean sendNotificationDevice(final StringBuilder text, final Contact contactTo, String action)
			throws Exception {
		if (Strings.isEmpty(text) || Strings.isEmpty(contactTo.getPushSystem())
				|| Strings.isEmpty(contactTo.getPushToken()))
			return false;
		Strings.replaceString(text, "\r", "");
		Strings.replaceString(text, "\t", "");
		Strings.replaceString(text, "\n", " ");
		if (text.length() > 100) {
			text.delete(100, text.length());
			if (text.lastIndexOf(" ") > 60)
				text.delete(text.lastIndexOf(" "), text.length());
			text.append("...");
		}
		try {
			if ("ios".equals(contactTo.getPushSystem()))
				ios.send(contactTo, text.toString(), action, getPingValues(contactTo).totalNew);
			else if ("android".equals(contactTo.getPushSystem()))
				android.send(contactTo, text.toString(), action);
			return true;
		} catch (NotFound | NotFoundException ex) {
			return false;
		} catch (Exception ex) {
			sendEmailSync(null, "ERROR", Strings.stackTraceToString(ex));
			return false;
		}
	}

	private List<String> compareAttributes(String attributes, String compareTo) {
		final List<String> a = new ArrayList<>();
		final String separator = "\u0015";
		if (!Strings.isEmpty(attributes) && !Strings.isEmpty(compareTo)) {
			final String[] split = attributes.toLowerCase().split(separator);
			compareTo = separator + compareTo.toLowerCase() + separator;
			for (int i = 0; i < split.length; i++) {
				if (compareTo.indexOf(separator + split[i] + separator) > -1)
					a.add(split[i]);
			}
		}
		return a;
	}

	private String attributesToString(List<String> a) {
		if (a.size() == 0)
			return "";
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < a.size(); i++)
			s.append("," + a.get(i));
		return s.substring(1);
	}

	private boolean userWantsNotification(NotificationID textID, Contact contact) {
		if (NotificationID.pwReset == textID || NotificationID.welcomeExt == textID)
			return true;
		if (NotificationID.newMsg == textID)
			return contact.getNotificationChat();
		if (NotificationID.friendReq == textID || NotificationID.friendAppro == textID)
			return contact.getNotificationFriendRequest();
		if (NotificationID.visitLocation == textID)
			return contact.getNotificationVisitLocation();
		if (NotificationID.visitProfile == textID)
			return contact.getNotificationVisitProfile();
		if (NotificationID.ratingProfile == textID)
			return contact.getNotificationVisitProfile();
		if (NotificationID.ratingLocMat == textID)
			return contact.getNotificationVisitLocation();
		if (NotificationID.markEvent == textID)
			return contact.getNotificationMarkEvent();
		if (NotificationID.birthday == textID)
			return contact.getNotificationBirthday();
		return true;
	}

	private void sendNotificationEmail(Contact contactFrom, Contact contactTo, StringBuilder note2, String action)
			throws IOException, MessagingException {
		final StringBuilder html = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.html"), StandardCharsets.UTF_8));
		final StringBuilder text = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.txt"), StandardCharsets.UTF_8));
		String s2;
		Strings.replaceString(html, "<jq:pseudonym />", contactTo.getPseudonym());
		Strings.replaceString(text, "<jq:pseudonym />", contactTo.getPseudonym());
		s2 = new SimpleDateFormat("dd.MM.yy HH:mm").format(new Date());
		Strings.replaceString(html, "<jq:time />", s2);
		Strings.replaceString(text, "<jq:time />", s2);
		s2 = note2.toString();
		Strings.replaceString(html, "<jq:text />", s2.replaceAll("\n", "<br />"));
		Strings.replaceString(text, "<jq:text />", s2);
		s2 = server + (Strings.isEmpty(action) ? "" : "?" + action);
		Strings.replaceString(html, "<jq:link />", s2);
		Strings.replaceString(text, "<jq:link />", s2);
		if (note2.indexOf("\n") > 0)
			note2.delete(note2.indexOf("\n"), note2.length());
		if (note2.indexOf("\r") > 0)
			note2.delete(note2.indexOf("\r"), note2.length());
		if (note2.length() > 80) {
			note2.delete(77, note2.length());
			note2.append("...");
		}
		sendEmail(contactTo.getEmail(), note2.toString(), text.toString(), html.toString());
	}

	public void sendEmailSync(final String to, final String subject, final String... text) throws MessagingException {
		final MimeMessage msg = email.createMimeMessage();
		final MimeMessageHelper helper = new MimeMessageHelper(msg, text != null && text.length > 1);
		helper.setFrom(from);
		helper.setTo(to == null ? from : to);
		helper.setSubject(subject);
		if (text != null) {
			if (text.length > 1)
				helper.setText(text[0], text[1]);
			else if (text.length > 0)
				helper.setText(text[0]);
		}
		email.send(msg);
	}

	@Async
	public void sendEmail(final String to, final String subject, final String... text) throws MessagingException {
		sendEmailSync(to, subject, text);
	}

	public static class Ping {
		private BigInteger userId;
		private Map<String, Integer> chatNew;
		private Map<String, Integer> chatUnseen;
		private int visit;
		private int friendRequest;
		private int notification;
		private int totalNew;

		public BigInteger getUserId() {
			return userId;
		}

		public Map<String, Integer> getChatNew() {
			return chatNew;
		}

		public Map<String, Integer> getChatUnseen() {
			return chatUnseen;
		}

		public int getVisit() {
			return visit;
		}

		public int getFriendRequest() {
			return friendRequest;
		}

		public int getNotification() {
			return notification;
		}

		public int getTotalNew() {
			return totalNew;
		}
	}
}
