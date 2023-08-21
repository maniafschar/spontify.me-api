package com.jq.findapp.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.commons.mail.resolver.DataSourceUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.ContactNotification.ContactNotificationType;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

import jakarta.ws.rs.NotFoundException;

@Service
public class NotificationService {
	@Autowired
	private Repository repository;

	@Autowired
	private MailCreateor mailCreateor;

	@Autowired
	private Text text;

	@Autowired
	private Android android;

	@Autowired
	private Ios ios;

	@Value("${app.server.webSocket}")
	private String serverWebSocket;

	@Value("${app.admin.id}")
	private BigInteger adminId;

	@Value("${app.mail.host}")
	private String emailHost;

	@Value("${app.mail.port}")
	private int emailPort;

	@Value("${app.mail.password}")
	private String emailPassword;

	public enum Environment {
		Production, Development
	}

	@Async
	public void locationNotifyOnMatch(final Contact me, final BigInteger locationId,
			final ContactNotificationTextType notificationTextType, final String param) throws Exception {
		final QueryParams params = new QueryParams(Query.location_listFavorite);
		params.setSearch("locationFavorite.locationId=" + locationId);
		final Result favorites = repository.list(params);
		final Location location = repository.one(Location.class, locationId);
		for (int i = 0; i < favorites.size(); i++)
			sendNotificationOnMatch(notificationTextType, me,
					repository.one(Contact.class, (BigInteger) favorites.get(i).get("locationFavorite.contactId")),
					location.getName(), param);
	}

	@Async
	public void sendNotificationOnMatch(final ContactNotificationTextType textID, final Contact me, final Contact other,
			final String... param) throws Exception {
		if (me != null && other != null && !me.getId().equals(other.getId()) && me.getAge() != null
				&& me.getGender() != null) {
			final String s2 = me.getGender().intValue() == 1 ? other.getAgeMale()
					: me.getGender().intValue() == 2 ? other.getAgeFemale()
							: other.getAgeDivers();
			if (!Strings.isEmpty(s2)) {
				final String[] s = s2.split(",");
				final int age = me.getAge().intValue();
				if (s2.indexOf(',') < 1 || ("18".equals(s[0]) || age >= Integer.parseInt(s[0]))
						&& ("99".equals(s[1]) || age <= Integer.parseInt(s[1]))) {
					final List<String> attribs = compareAttributes(me.getSkills(), other.getSkills());
					final List<String> attribsEx = compareAttributes(me.getSkillsText(), other.getSkillsText());
					if (attribs.size() > 0 || attribsEx.size() > 0) {
						final String[] param2 = new String[param == null ? 1 : param.length + 1];
						param2[0] = "" + (attribs.size() + attribsEx.size());
						if (param != null) {
							for (int i = 0; i < param.length; i++)
								param2[i + 1] = param[i];
						}
						sendNotification(me, other, textID, Strings.encodeParam("p=" + me.getId()), param2);
					}
				}
			}
		}
	}

	public boolean sendNotification(final Contact contactFrom, final Contact contactTo,
			final ContactNotificationTextType notificationTextType,
			final String action, final String... param) throws Exception {
		if (!contactTo.getVerified())
			return false;
		if (contactTo.getId() != null) {
			final QueryParams params = new QueryParams(Query.contact_block);
			params.setUser(contactFrom);
			params.setSearch("block.contactId=" + contactFrom.getId() + " and block.contactId2="
					+ contactTo.getId() + " or block.contactId="
					+ contactTo.getId() + " and block.contactId2=" + contactFrom.getId());
			if (repository.list(params).size() > 0)
				return false;
		}
		final StringBuilder s = new StringBuilder(
				text.getText(contactTo, TextId.valueOf("notification_" + notificationTextType.name())));
		if (param != null) {
			for (int i = 0; i < param.length; i++)
				Strings.replaceString(s, "<jq:EXTRA_" + (i + 1) + " />", param[i]);
		}
		if (s.length() > 250) {
			s.delete(247, s.length());
			if (s.lastIndexOf(" ") > 180)
				s.delete(s.lastIndexOf(" "), s.length());
			s.append("...");
		}
		WebClient.create(serverWebSocket + "refresh/" + contactTo.getId()).post()
				.bodyValue(getPingValues(contactTo)).retrieve().toBodilessEntity();
		ContactNotification notification = null;
		if (notificationTextType != ContactNotificationTextType.chatNew
				&& notificationTextType != ContactNotificationTextType.contactVideoCall
				&& (notification = save(contactTo, contactFrom, s.toString(), action, notificationTextType)) == null)
			return false;
		if (userWantsNotification(notificationTextType, contactTo)) {
			boolean b = !Strings.isEmpty(contactTo.getPushSystem()) && !Strings.isEmpty(contactTo.getPushToken());
			if (b)
				b = sendNotificationDevice(contactFrom, contactTo, s, action, notification);
			if (!b) {
				sendNotificationEmail(contactFrom, contactTo, s.toString(), action);
				if (notification != null)
					notification.setType(ContactNotificationType.email);
			}
			if (notification != null)
				repository.save(notification);
			return true;
		}
		return false;
	}

	private ContactNotification save(final Contact contactTo, final Contact contactFrom, final String text,
			final String action,
			final ContactNotificationTextType notificationTextType) throws Exception {
		final QueryParams params = new QueryParams(Query.contact_notification);
		params.setSearch("contactNotification.contactId=" + contactTo.getId() +
				" and contactNotification.contactId2=" + contactFrom.getId() +
				" and TIMESTAMPDIFF(HOUR,contactNotification.createdAt,current_timestamp)<24" +
				" and contactNotification.action='" + (action == null ? "" : action) +
				"' and contactNotification.textType='" + notificationTextType.name() + "'");
		if (repository.list(params).size() > 0)
			return null;
		final ContactNotification notification = new ContactNotification();
		notification.setAction(action);
		notification.setContactId(contactTo.getId());
		notification.setContactId2(contactFrom.getId());
		notification.setText(text);
		notification.setTextType(notificationTextType);
		repository.save(notification);
		return notification;
	}

	public Ping getPingValues(final Contact contact) {
		final QueryParams params = new QueryParams(Query.contact_pingNotification);
		params.setUser(contact);
		params.setLimit(0);
		final Ping values = new Ping();
		values.userId = contact.getId();
		values.notification = ((Number) repository.one(params).get("_c")).intValue();
		params.setQuery(Query.contact_pingChat);
		values.chat.put("firstId", (BigInteger) repository.one(params).get("_c"));
		params.setQuery(Query.contact_pingChatNew);
		Result list = repository.list(params);
		final Map<BigInteger, String> chatNew = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			chatNew.put((BigInteger) row.get("contact.id"), (String) row.get("contact.pseudonym"));
		}
		values.chat.put("new", chatNew);
		params.setQuery(Query.contact_pingChatUnseen);
		list = repository.list(params);
		final Map<String, Integer> chatUnseen = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			chatUnseen.put("" + row.get("contactChat.contactId2"), ((Number) row.get("_c")).intValue());
		}
		values.chat.put("unseen", chatUnseen);
		return values;
	}

	private boolean sendNotificationDevice(final Contact contactFrom, final Contact contactTo, final StringBuilder text,
			final String action, final ContactNotification notification) throws Exception {
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
			final String notificationId = notification == null ? "" : notification.getId().toString();
			final String s = text.toString().replace("\"", "\\\"");
			if ("ios".equals(contactTo.getPushSystem())) {
				final Ping p = getPingValues(contactTo);
				ios.send(contactFrom, contactTo, s, action, ((Map<?, ?>) p.chat.get("new")).size() + p.notification,
						notificationId);
			} else if ("android".equals(contactTo.getPushSystem()))
				android.send(contactFrom, contactTo, s, action, notificationId);
			if (notification != null)
				notification.setType(ContactNotificationType.valueOf(contactTo.getPushSystem()));
			return true;
		} catch (NotFound | NotFoundException ex) {
			contactTo.setPushSystem(null);
			contactTo.setPushToken(null);
			repository.save(contactTo);
			return false;
		} catch (final Exception ex) {
			final QueryParams params = new QueryParams(Query.misc_setting);
			params.setSearch("setting.label like 'push.gen.%'");
			final Result settings = repository.list(params);
			String setting = "\n\n";
			for (int i = 0; i < settings.size(); i++)
				setting += new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(settings.get(i))
						+ "\n\n";
			createTicket(TicketType.ERROR, "Push Notification",
					contactTo.getId() + "\n\n"
							+ IOUtils.toString(getClass().getResourceAsStream("/template/push.android"),
									StandardCharsets.UTF_8)
									.replace("{to}", contactTo.getPushToken())
									.replace("{text}", text)
									.replace("{notificationId}",
											"" + (notification == null ? null : notification.getId()))
									.replace("{exec}", Strings.isEmpty(action) ? "" : action)
							+ (ex instanceof WebClientResponseException
									? "\n\n" + ((WebClientResponseException) ex).getResponseBodyAsString()
									: "")
							+ setting + Strings.stackTraceToString(ex),
					notification == null ? null : notification.getContactId2());
			return false;
		}
	}

	private List<String> compareAttributes(final String attributes, String compareTo) {
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

	private boolean userWantsNotification(final ContactNotificationTextType notificationTextType,
			final Contact contact) {
		if (ContactNotificationTextType.chatNew == notificationTextType)
			return contact.getNotificationChat();
		if (ContactNotificationTextType.contactFriendRequest == notificationTextType
				|| ContactNotificationTextType.contactFriendApproved == notificationTextType)
			return contact.getNotificationFriendRequest();
		if (ContactNotificationTextType.contactVisitLocation == notificationTextType)
			return contact.getNotificationVisitLocation();
		if (ContactNotificationTextType.contactVisitProfile == notificationTextType)
			return contact.getNotificationVisitProfile();
		if (ContactNotificationTextType.eventParticipate == notificationTextType)
			return contact.getNotificationMarkEvent();
		if (ContactNotificationTextType.contactBirthday == notificationTextType)
			return contact.getNotificationBirthday();
		return true;
	}

	public void sendNotificationEmail(final Contact contactFrom, final Contact contactTo, String message,
			final String action)
			throws Exception {
		final StringBuilder html = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.html"), StandardCharsets.UTF_8));
		final StringBuilder s = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.txt"), StandardCharsets.UTF_8));
		final String url = repository.one(Client.class, contactTo.getClientId()).getUrl();
		String s2;
		Strings.replaceString(html, "<jq:logo />", url + "/images/logo.png");
		Strings.replaceString(html, "<jq:pseudonym />", contactTo.getPseudonym());
		Strings.replaceString(s, "<jq:pseudonym />", contactTo.getPseudonym());
		s2 = Strings.formatDate(null, new Date(), contactTo.getTimezone());
		Strings.replaceString(html, "<jq:time />", s2);
		Strings.replaceString(s, "<jq:time />", s2);
		s2 = message;
		Strings.replaceString(html, "<jq:text />", s2.replaceAll("\n", "<br />"));
		Strings.replaceString(s, "<jq:text />", sanatizeHtml(s2));
		if (Strings.isEmpty(action))
			s2 = url;
		else if (action.startsWith("https://"))
			s2 = action;
		else
			s2 = url + "?" + action;
		Strings.replaceString(html, "<jq:link />", s2);
		Strings.replaceString(s, "<jq:link />", s2);
		Strings.replaceString(html, "<jq:url />", url);
		Strings.replaceString(s, "<jq:url />", url);
		if (contactFrom == null || contactTo.getId() != null && contactFrom.getId().equals(contactTo.getId()))
			s2 = text.getText(contactTo, TextId.mail_title);
		else
			s2 = text.getText(contactTo, TextId.mail_titleFrom).replaceAll("<jq:pseudonymFrom />",
					contactFrom.getPseudonym());
		Strings.replaceString(html, "<jq:newsTitle />", s2);
		Strings.replaceString(s, "<jq:newsTitle />", s2);
		if (contactFrom != null && contactFrom.getImage() != null) {
			final QueryParams params = new QueryParams(Query.contact_list);
			params.setUser(contactFrom);
			params.setSearch("contact.id=" + contactFrom.getId());
			Strings.replaceString(html, "<jq:image />",
					"<img src=\"" + url + "/med/" + repository.one(params).get("contact.image")
							+ "\" width=\"150\" height=\"150\" style=\"height:150px;min-height:150px;max-height:150px;width:150px;min-width:150px;max-width:150px;border-radius:75px;\" />");
		} else
			Strings.replaceString(html, "<jq:image />", "");
		final JsonNode css = new ObjectMapper()
				.readTree(repository.one(Client.class, contactTo.getClientId()).getStorage()).get("css");
		css.fieldNames().forEachRemaining(key -> Strings.replaceString(html, "--" + key, css.get(key).asText()));
		message = sanatizeHtml(message);
		if (message.indexOf("\n") > 0)
			message = message.substring(0, message.indexOf("\n"));
		if (message.indexOf("\r") > 0)
			message = message.substring(0, message.indexOf("\r"));
		if (message.indexOf(".", 45) > 0)
			message = message.substring(0, message.indexOf(".", 45) + 1);
		if (message.indexOf("?", 45) > 0)
			message = message.substring(0, message.indexOf("?", 45) + 1);
		if (message.indexOf("!", 45) > 0)
			message = message.substring(0, message.indexOf("!", 45) + 1);
		if (message.length() > 80) {
			message = message.substring(0, 81);
			if (message.lastIndexOf(" ") > 30)
				message = message.substring(0, message.lastIndexOf(" "));
			else
				message = message.substring(0, 77);
			message += "...";
		}
		sendEmail(contactTo, message, s.toString(), html.toString());
	}

	private String sanatizeHtml(String s) {
		s = s.replace("<ul>", "\n\n");
		s = s.replace("<li>", "* ");
		s = s.replace("</li>", "\n");
		s = s.replace("</ul>", "\n");
		return s.replaceAll("<[^>]*>", "");
	}

	private void sendEmail(final Contact to, final String subject, final String text, final String html)
			throws Exception {
		final String from = to == null ? repository.one(Contact.class, adminId).getEmail()
				: repository.one(Client.class, to.getClientId()).getEmail();
		final ImageHtmlEmail email = mailCreateor.create();
		email.setHostName(emailHost);
		email.setSmtpPort(emailPort);
		email.setCharset(StandardCharsets.UTF_8.name());
		email.setAuthenticator(new DefaultAuthenticator(from, emailPassword));
		email.setSSLOnConnect(true);
		email.setFrom(from);
		email.addTo(to == null ? from : to.getEmail());
		email.setSubject(subject);
		email.setTextMsg(text);
		if (html != null && to != null) {
			email.setDataSourceResolver(
					new DataSourceUrlResolver(new URL(repository.one(Client.class, to.getClientId()).getUrl())));
			email.setHtmlMsg(html);
		} else
			email.setHtmlMsg(text);
		if (to != null)
			createTicket(TicketType.EMAIL, to.getEmail(), text, adminId);
		email.send();
	}

	public void createTicket(final TicketType type, String subject, String text, final BigInteger user) {
		try {
			if (subject == null)
				subject = "no subject";
			else if (subject.length() > 255) {
				text = "..." + subject.substring(252) + "\n\n" + text;
				subject = subject.substring(0, 252) + "...";
			}
			final QueryParams params = new QueryParams(Query.misc_listTicket);
			params.setSearch("subject='" + subject + "' and type='" + type.name() + "' and createdAt>'"
					+ Instant.now().minus(Duration.ofDays(1)) + "' and contactId=" + user);
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				if (text.equals(result.get(i).get("ticket.note")))
					return;
			}
			final Ticket ticket = new Ticket();
			ticket.setSubject(subject);
			ticket.setNote(text);
			ticket.setType(type);
			ticket.setContactId(user);
			repository.save(ticket);
			if (type == TicketType.BLOCK)
				sendEmail(null, "Block", text, null);
		} catch (final Exception ex) {
			try {
				sendEmail(null, type + ": " + subject, text, null);
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

	private byte[] imageRound(final byte[] img) throws IOException {
		final BufferedImage image = ImageIO.read(new ByteArrayInputStream(img));
		final int w = image.getWidth();
		final int h = image.getHeight();
		final BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = output.createGraphics();
		g2.setComposite(AlphaComposite.Src);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(Color.WHITE);
		g2.fill(new RoundRectangle2D.Float(0, 0, w, h, w, h));
		g2.setComposite(AlphaComposite.SrcAtop);
		g2.drawImage(image, 0, 0, null);
		g2.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(output, "png", out);
		return out.toByteArray();
	}

	public static class Ping {
		private BigInteger userId;
		private Boolean recommend;
		private int notification;
		private final Map<String, Object> chat = new HashMap<>();

		public BigInteger getUserId() {
			return userId;
		}

		public Map<String, Object> getChat() {
			return chat;
		}

		public int getNotification() {
			return notification;
		}

		public Boolean getRecommend() {
			return recommend;
		}
	}

	@Component
	public static class MailCreateor {
		public ImageHtmlEmail create() {
			return new ImageHtmlEmail();
		}
	}
}