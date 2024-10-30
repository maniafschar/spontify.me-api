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
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
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
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.entity.ContactNotification;
import com.jq.findapp.entity.ContactNotification.ContactNotificationType;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

import jakarta.ws.rs.NotFoundException;

@Service
public class NotificationService {
	private static volatile boolean sendingEmailPaused = false;

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

	@Value("${app.mail.host}")
	private String emailHost;

	@Value("${app.mail.port}")
	private int emailPort;

	@Value("${app.mail.password}")
	private String emailPassword;

	public enum Environment {
		Production, Development
	}

	public enum NotificationType {
		birthday, chat, engagement, friend, event, news, visitLocation, visitProfile
	}

	@Async
	public void locationNotifyOnMatch(final Contact me, final BigInteger locationId,
			final TextId textId, final String param) {
		final QueryParams params = new QueryParams(Query.location_listFavorite);
		params.setSearch("locationFavorite.locationId=" + locationId);
		final Result favorites = repository.list(params);
		final Location location = repository.one(Location.class, locationId);
		for (int i = 0; i < favorites.size(); i++)
			sendNotificationOnMatch(textId, me,
					repository.one(Contact.class, (BigInteger) favorites.get(i).get("locationFavorite.contactId")),
					location.getName(), param);
	}

	@Async
	public void sendNotificationOnMatch(final TextId textId, final Contact me, final Contact other,
			final String... param) {
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
						sendNotificationSync(me, other, textId, Strings.encodeParam("p=" + me.getId()), param2);
					}
				}
			}
		}
	}

	public boolean sendNotificationSync(final Contact contactFrom, final Contact contactTo,
			final TextId textId, final String action, final String... param) {
		if (contactTo == null || !contactTo.getVerified())
			return false;
		if (contactFrom != null && contactTo.getId() != null) {
			final QueryParams params = new QueryParams(Query.contact_block);
			params.setUser(contactFrom);
			params.setSearch("block.contactId=" + contactFrom.getId() + " and block.contactId2="
					+ contactTo.getId() + " or block.contactId="
					+ contactTo.getId() + " and block.contactId2=" + contactFrom.getId());
			if (repository.list(params).size() > 0)
				return false;
		}
		final StringBuilder s = new StringBuilder(text.getText(contactTo, textId));
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
				.header("Content-Type", "application/json")
				.bodyValue(getPingValues(contactTo)).retrieve().toBodilessEntity().block();
		ContactNotification notification = null;
		if (textId != TextId.notification_chatNew
				&& textId != TextId.notification_contactVideoCall
				&& (notification = save(contactTo, contactFrom, s.toString(), action, textId)) == null)
			return false;
		if (userWantsNotification(textId, contactTo)) {
			boolean b = !Strings.isEmpty(contactTo.getPushSystem()) && !Strings.isEmpty(contactTo.getPushToken());
			if (b)
				b = sendNotificationDevice(contactFrom, contactTo, s, action, notification);
			if (!b && textId != TextId.notification_clientNews) {
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

	@Async
	public void sendNotification(final Contact contactFrom, final Contact contactTo,
			final TextId textId, final String action, final String... param) {
		sendNotificationSync(contactFrom, contactTo, textId, action, param);
	}

	private ContactNotification save(final Contact contactTo, final Contact contactFrom, final String text,
			final String action, final TextId textId) {
		final BigInteger fromId = contactFrom == null ? null : contactFrom.getId();
		final QueryParams params = new QueryParams(Query.contact_notification);
		params.setSearch("contactNotification.contactId=" + contactTo.getId() +
				" and contactNotification.contactId2" + (fromId == null ? " is null" : "=" + fromId) +
				" and TIMESTAMPDIFF(HOUR,contactNotification.createdAt,current_timestamp)<24" +
				" and contactNotification.action='" + (action == null ? "" : action) +
				"' and contactNotification.textId='" + textId.name() + "'");
		if (repository.list(params).size() > 0)
			return null;
		final ContactNotification notification = new ContactNotification();
		notification.setAction(action);
		notification.setContactId(contactTo.getId());
		notification.setContactId2(fromId);
		notification.setText(text);
		notification.setTextId(textId);
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
			final String action, final ContactNotification notification) {
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
		final String from = contactFrom == null ? repository.one(Client.class, contactTo.getClientId()).getName()
				: contactFrom.getPseudonym();
		try {
			final String notificationId = notification == null ? "" : notification.getId().toString();
			final String s = text.toString().replace("\"", "\\\"");
			if ("ios".equals(contactTo.getPushSystem())) {
				final Ping p = getPingValues(contactTo);
				ios.send(from, contactTo, s, action,
						((Map<?, ?>) p.chat.get("new")).size() + p.notification,
						notificationId);
			} else if ("android".equals(contactTo.getPushSystem()))
				android.send(from, contactTo, s, action, notificationId);
			if (notification != null)
				notification.setType(ContactNotificationType.valueOf(contactTo.getPushSystem()));
			return true;
		} catch (NotFound | NotFoundException ex) {
			contactTo.setPushToken(null);
			contactTo.setPushSystem(null);
			repository.save(contactTo);
			return false;
		} catch (final Exception ex) {
			final QueryParams params = new QueryParams(Query.misc_setting);
			params.setSearch("setting.label like 'push.gen.%'");
			final Result settings = repository.list(params);
			String setting = "\n\n";
			try {
				for (int i = 0; i < settings.size(); i++)
					setting += Json.toPrettyString(settings.get(i)) + "\n\n";
				createTicket(TicketType.ERROR, "Push Notification",
						contactTo.getId() + "\n\n"
								+ IOUtils.toString(getClass().getResourceAsStream("/template/push.android"),
										StandardCharsets.UTF_8)
										.replace("{from}", from)
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
			} catch (Exception ex2) {
				throw new RuntimeException(ex.getMessage(), ex2);
			}
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

	private boolean userWantsNotification(final TextId textId, final Contact contact) {
		if (TextId.notification_chatNew == textId)
			return contact.getNotification().contains(NotificationType.chat.name());
		if (TextId.notification_contactFriendRequest == textId
				|| TextId.notification_contactFriendApproved == textId)
			return contact.getNotification().contains(NotificationType.friend.name());
		if (TextId.notification_contactVisitLocation == textId)
			return contact.getNotification().contains(NotificationType.visitLocation.name());
		if (TextId.notification_contactVisitProfile == textId)
			return contact.getNotification().contains(NotificationType.visitProfile.name());
		if (TextId.notification_eventParticipate == textId)
			return contact.getNotification().contains(NotificationType.event.name());
		if (TextId.notification_contactBirthday == textId)
			return contact.getNotification().contains(NotificationType.birthday.name());
		if (TextId.notification_clientNews == textId ||
				textId.name().startsWith(TextId.notification_clientMarketing.name()))
			return contact.getNotification().contains(NotificationType.news.name());
		return true;
	}

	public void sendNotificationEmail(final Contact contactFrom, final Contact contactTo, String message,
			final String action) {
		try (final InputStream inHtml = getClass().getResourceAsStream("/template/email.html");
				final InputStream inText = getClass().getResourceAsStream("/template/email.txt")) {
			final StringBuilder html = new StringBuilder(IOUtils.toString(inHtml, StandardCharsets.UTF_8));
			final StringBuilder s = new StringBuilder(IOUtils.toString(inText, StandardCharsets.UTF_8));
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
			if (contactFrom == null || contactFrom.getType() == ContactType.adminContent
					|| contactTo.getId() != null && contactFrom.getId().equals(contactTo.getId()))
				s2 = text.getText(contactTo, TextId.mail_title);
			else
				s2 = text.getText(contactTo, TextId.mail_titleFrom).replaceAll("<jq:pseudonymFrom />",
						contactFrom.getPseudonym());
			Strings.replaceString(html, "<jq:newsTitle />", s2);
			Strings.replaceString(s, "<jq:newsTitle />", s2);
			if (contactFrom != null && contactFrom.getType() != ContactType.adminContent
					&& contactFrom.getImageList() != null)
				Strings.replaceString(html, "<jq:image />",
						"<br /><img src=\"" + Strings.removeSubdomain(url) + "/med/"
								+ Attachment.resolve(contactFrom.getImageList())
								+ "\" width=\"150\" height=\"150\" style=\"height:150px;min-height:150px;max-height:150px;width:150px;min-width:150px;max-width:150px;border-radius:75px;\" />");
			else
				Strings.replaceString(html, "<jq:image />", "");
			final JsonNode css = Json
					.toNode(Attachment.resolve(repository.one(Client.class, contactTo.getClientId()).getStorage()))
					.get("css");
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
			final Client client = repository.one(Client.class, contactTo.getClientId());
			sendEmail(client,
					contactFrom == null || contactFrom.getId().equals(client.getAdminId()) ? null
							: contactFrom.getPseudonym(),
					contactTo.getEmail(), message, s.toString(), html.toString());
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	private String sanatizeHtml(String s) {
		s = s.replace("<br/>", "\n");
		s = s.replace("<ul>", "\n\n");
		s = s.replace("<li>", "* ");
		s = s.replace("</li>", "\n");
		s = s.replace("</ul>", "\n");
		return s.replaceAll("<[^>]*>", "");
	}

	public void sendEmail(final Client client, final String name, final String to, final String subject,
			final String text, final String html) {
		if (sendingEmailPaused) {
			sendEmailAsync(client, name, to, subject, text, html);
			return;
		}
		final ImageHtmlEmail email = mailCreateor.create();
		email.setHostName(emailHost);
		email.setSmtpPort(emailPort);
		email.setCharset(StandardCharsets.UTF_8.name());
		email.setAuthenticator(new DefaultAuthenticator(client.getEmail(), emailPassword));
		email.setSSLOnConnect(true);
		try {
			email.setFrom(client.getEmail(), (Strings.isEmpty(name) ? "" : name + " · ") + client.getName());
			email.addTo(to);
			email.setSubject(subject);
			email.setTextMsg(Strings.sanitize(text, 0));
			if (html != null) {
				email.setDataSourceResolver(new DataSourceUrlResolver(
						URI.create(Strings.removeSubdomain(client.getUrl())).toURL()));
				email.setHtmlMsg(html);
			}
			email.send();
			createTicket(TicketType.EMAIL, to, subject + "\n" + text, client.getAdminId());
		} catch (final EmailException | MalformedURLException ex) {
			if (Strings.stackTraceToString(ex).contains("450 4.7.0")) {
				sendingEmailPaused = true;
				sendEmailAsync(client, name, to, subject, text, html);
			} else
				createTicket(TicketType.ERROR, "Email exception: " + to,
						Strings.stackTraceToString(ex) + "\n\n" + text, client.getAdminId());
		}
	}

	@Async
	private void sendEmailAsync(final Client client, final String name, final String to, final String subject,
			final String text, final String html) {
		try {
			Thread.sleep(300000);
			sendingEmailPaused = false;
			sendEmail(client, name, to, subject, text, html);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public void createTicket(final TicketType type, String subject, String text, final BigInteger contactId) {
		try {
			if (type == TicketType.ERROR && !"REGEX".equals(subject)) {
				final QueryParams params = new QueryParams(Query.misc_listStorage);
				params.setSearch("storage.label='logErrorExclusionRegex'");
				final Map<String, Object> exclude = repository.one(params);
				if (exclude != null) {
					try {
						if (Pattern.compile((String) exclude.get("storage.storage")).matcher(text).find())
							return;
					} catch (Exception ex) {
						createTicket(TicketType.ERROR, "REGEX", ex.getMessage() + "\n" + exclude.get("storage.storage"),
								null);
					}
				}
			}
			if (subject == null)
				subject = "no subject";
			else if (subject.length() > 255) {
				text = "..." + subject.substring(252) + "\n\n" + text;
				subject = subject.substring(0, 252) + "...";
			}
			final QueryParams params = new QueryParams(Query.misc_listTicket);
			params.setSearch("subject='" + subject + "' and type='" + type.name() + "' and createdAt>cast('"
					+ Instant.now().minus(Duration.ofDays(1)) + "' as timestamp)"
					+ (contactId == null ? "" : " and contactId=" + contactId));
			final Result result = repository.list(params);
			for (int i = 0; i < result.size(); i++) {
				if (text.equals(result.get(i).get("ticket.note")))
					return;
			}
			final Ticket ticket = new Ticket();
			ticket.setSubject(subject);
			ticket.setNote(text);
			if (contactId != null)
				ticket.setClientId(repository.one(Contact.class, contactId).getClientId());
			ticket.setType(type);
			if (contactId != null)
				ticket.setContactId(contactId);
			repository.save(ticket);
		} catch (final Exception ex) {
			ex.printStackTrace();
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