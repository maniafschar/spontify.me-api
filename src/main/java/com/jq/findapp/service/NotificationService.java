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
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.activation.DataSource;
import javax.imageio.ImageIO;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.NotFoundException;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.push.Android;
import com.jq.findapp.service.push.Ios;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;

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

	@Value("${app.admin.id}")
	private BigInteger adminId;

	private static final byte[] LOGO;

	static {
		try {
			LOGO = IOUtils.toByteArray(NotificationService.class.getResourceAsStream("/template/logoEmail.png"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public enum Environment {
		Production, Development
	}

	@Async
	public void locationNotifyOnMatch(final Contact me, final BigInteger locationId,
			final ContactNotificationTextType notificationTextType, String param) throws Exception {
		final QueryParams params = new QueryParams(Query.location_listFavorite);
		params.setSearch("locationFavorite.locationId=" + locationId);
		final Result favorites = repository.list(params);
		final Location location = repository.one(Location.class, locationId);
		for (int i = 0; i < favorites.size(); i++)
			sendNotificationOnMatch(notificationTextType,
					me,
					repository.one(Contact.class, (BigInteger) favorites.get(i).get("locationFavorite.contactId")),
					location.getName(), param);
	}

	@Async
	public String[] sendNotificationOnMatch(ContactNotificationTextType textID, final Contact me, final Contact other,
			String... param) throws Exception {
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
						String[] param2 = new String[param == null ? 1 : param.length + 1];
						param2[0] = "" + (attribs.size() + attribsEx.size());
						if (param != null) {
							for (int i = 0; i < param.length; i++)
								param2[i + 1] = param[i];
						}
						if (sendNotification(me, other, textID, Strings.encodeParam("p=" + me.getId()), param2))
							return new String[] { attributesToString(attribs), attributesToString(attribsEx) };
					}
				}
			}
		}
		return null;
	}

	public boolean sendNotification(Contact contactFrom, Contact contactTo,
			ContactNotificationTextType notificationTextType,
			String action, String... param) throws Exception {
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
		final StringBuilder text = new StringBuilder(
				Text.valueOf("mail_" + notificationTextType).getText(contactTo.getLanguage()));
		if (param != null) {
			for (int i = 0; i < param.length; i++)
				Strings.replaceString(text, "<jq:EXTRA_" + (i + 1) + " />", param[i]);
		}
		if (text.charAt(0) < 'A' || text.charAt(0) > 'Z')
			text.insert(0, contactFrom.getPseudonym() + (text.charAt(0) == ':' ? "" : " "));
		if (text.length() > 250) {
			text.delete(247, text.length());
			if (text.lastIndexOf(" ") > 180)
				text.delete(text.lastIndexOf(" "), text.length());
			text.append("...");
		}
		ContactNotification notification = null;
		if (notificationTextType != ContactNotificationTextType.chatNew
				&& (notification = save(contactTo, contactFrom, text.toString(), action, notificationTextType)) == null)
			return false;
		if (userWantsNotification(notificationTextType, contactTo)) {
			boolean b = !Strings.isEmpty(contactTo.getPushSystem()) && !Strings.isEmpty(contactTo.getPushToken());
			if (b)
				b = sendNotificationDevice(text, contactTo, action, notification);
			if (!b) {
				sendNotificationEmail(contactFrom, contactTo, text.toString(), action);
				if (notification != null)
					notification.setType(ContactNotificationType.email);
			}
			if (notification != null)
				repository.save(notification);
			return true;
		}
		return false;
	}

	private ContactNotification save(Contact contactTo, Contact contactFrom, String text, String action,
			ContactNotificationTextType notificationTextType) throws Exception {
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

	public Ping getPingValues(Contact contact) {
		final QueryParams params = new QueryParams(Query.contact_pingNotification);
		params.setUser(contact);
		params.setLimit(0);
		final Ping values = new Ping();
		values.userId = contact.getId();
		values.notification = ((Number) repository.one(params).get("_c")).intValue();
		values.totalNew = values.notification;
		params.setQuery(Query.contact_pingChat);
		values.firstChatId = (BigInteger) repository.one(params).get("_c");
		params.setQuery(Query.contact_pingChatNew);
		Result list = repository.list(params);
		values.chatNew = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			values.chatNew.put((BigInteger) row.get("contact.id"), (String) row.get("contact.pseudonym"));
			values.totalNew++;
		}
		params.setQuery(Query.contact_pingChatUnseen);
		list = repository.list(params);
		values.chatUnseen = new HashMap<>();
		for (int i = 0; i < list.size(); i++) {
			final Map<String, Object> row = list.get(i);
			values.chatUnseen.put("" + row.get("contactChat.contactId2"), ((Number) row.get("_c")).intValue());
		}
		return values;
	}

	private boolean sendNotificationDevice(final StringBuilder text, final Contact contactTo, String action,
			ContactNotification notification) throws Exception {
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
			if ("ios".equals(contactTo.getPushSystem()))
				ios.send(contactTo, s, action, getPingValues(contactTo).totalNew, notificationId);
			else if ("android".equals(contactTo.getPushSystem()))
				android.send(contactTo, s, action, notificationId);
			if (notification != null)
				notification.setType(ContactNotificationType.valueOf(contactTo.getPushSystem()));
			return true;
		} catch (NotFound | NotFoundException ex) {
			contactTo.setPushSystem(null);
			contactTo.setPushToken(null);
			repository.save(contactTo);
			return false;
		} catch (Exception ex) {
			final QueryParams param = new QueryParams(Query.misc_setting);
			param.setSearch("setting.label like 'push.gen.%'");
			final Result settings = repository.list(param);
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

	private boolean userWantsNotification(ContactNotificationTextType notificationTextType, Contact contact) {
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

	public void sendNotificationEmail(Contact contactFrom, Contact contactTo, String message, String action)
			throws Exception {
		final StringBuilder html = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.html"), StandardCharsets.UTF_8));
		final StringBuilder text = new StringBuilder(
				IOUtils.toString(getClass().getResourceAsStream("/template/email.txt"), StandardCharsets.UTF_8));
		String s2;
		Strings.replaceString(html, "<jq:pseudonym />", contactTo.getPseudonym());
		Strings.replaceString(text, "<jq:pseudonym />", contactTo.getPseudonym());
		s2 = Strings.formatDate(null, new Date(), contactTo.getTimezone());
		Strings.replaceString(html, "<jq:time />", s2);
		Strings.replaceString(text, "<jq:time />", s2);
		s2 = message;
		if (s2.startsWith(contactFrom.getPseudonym() + ": "))
			s2 = s2.substring(contactFrom.getPseudonym().length() + 2).trim();
		Strings.replaceString(html, "<jq:text />", s2.replaceAll("\n", "<br />").replaceAll("spontify.me",
				"<a href=\"https://spontify.me\" style=\"color:rgb(246,255,187);text-decoration:none;\">spontify.me</a>"));
		Strings.replaceString(text, "<jq:text />", s2);
		if (Strings.isEmpty(action))
			s2 = server;
		else if (action.startsWith("https://"))
			s2 = action;
		else
			s2 = server + "?" + action;
		Strings.replaceString(html, "<jq:link />", s2);
		Strings.replaceString(text, "<jq:link />", s2);
		Strings.replaceString(html, "<jq:url />", Strings.URL_APP);
		Strings.replaceString(text, "<jq:url />", Strings.URL_APP);
		if (contactFrom == null || contactTo.getId() != null && contactFrom.getId().equals(contactTo.getId()))
			s2 = Text.mail_newsTitle.getText(contactTo.getLanguage());
		else
			s2 = Text.mail_newsTitleFrom.getText(contactTo.getLanguage()).replaceAll("<jq:pseudonymFrom />",
					contactFrom.getPseudonym());
		Strings.replaceString(html, "<jq:newsTitle />", s2);
		Strings.replaceString(text, "<jq:newsTitle />", s2);
		byte[] imgProfile = null;
		if (contactFrom == null || contactFrom.getImage() != null) {
			imgProfile = Attachment.getFile(contactFrom.getImage());
			Strings.replaceString(html, "<jq:image />",
					"<img style=\"height:150px;min-height:150px;max-height:150px;width:150px;min-width:150px;max-width:150px;\" src=\"cid:img_profile\" width=\"150\" height=\"150\" />");
		} else
			Strings.replaceString(html, "<jq:image />", "");
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
		sendEmail(contactTo.getEmail(), message, imgProfile, text.toString(), html.toString());
	}

	private void sendEmail(final String to, final String subject,
			final byte[] imgProfile, final String text, String html) throws Exception {
		final MimeMessage msg = email.createMimeMessage();
		final MimeMessageHelper helper = new MimeMessageHelper(msg, html != null);
		helper.setFrom(from);
		helper.setTo(to == null ? from : to);
		helper.setSubject(subject);
		if (html != null) {
			helper.setText(text, html);
			helper.addInline("img_logo", new MyDataSource(LOGO, "logoEmail.png"));
			if (imgProfile != null)
				helper.addInline("img_profile", new MyDataSource(imageRound(imgProfile), "image.jpg"));
		} else
			helper.setText(text);
		createTicket(TicketType.EMAIL, to, text, adminId);
		email.send(msg);
	}

	public void createTicket(TicketType type, String subject, String text, BigInteger user) {
		try {
			if (subject == null)
				subject = "no subject";
			if (subject.length() > 255) {
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
				sendEmail(null, "Block", null, text, null);
		} catch (Exception ex) {
			try {
				sendEmail(null, type + ": " + subject, null, text, null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private byte[] imageRound(byte[] img) throws IOException {
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
		private BigInteger firstChatId;
		private BigInteger userId;
		private Map<BigInteger, String> chatNew;
		private Map<String, Integer> chatUnseen;
		private int chat;
		private int notification;
		private int totalNew;

		public BigInteger getUserId() {
			return userId;
		}

		public Map<BigInteger, String> getChatNew() {
			return chatNew;
		}

		public Map<String, Integer> getChatUnseen() {
			return chatUnseen;
		}

		public int getChat() {
			return chat;
		}

		public BigInteger getFirstChatId() {
			return firstChatId;
		}

		public int getNotification() {
			return notification;
		}

		public int getTotalNew() {
			return totalNew;
		}
	}

	private class MyDataSource implements DataSource {
		private final byte[] data;
		private final String name;

		private MyDataSource(byte[] data, String name) {
			this.data = data;
			this.name = name;
		}

		@Override
		public OutputStream getOutputStream() throws IOException {
			return null;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getContentType() {
			return "image/" + name.substring(name.lastIndexOf('.') + 1).toLowerCase();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return new ByteArrayInputStream(data);
		}
	}
}
