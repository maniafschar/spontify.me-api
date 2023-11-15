package com.jq.findapp.service.backend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.batik.ext.awt.RadialGradientPaint;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.ImageHtmlEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.NotificationService.MailCreateor;
import com.jq.findapp.util.Strings;

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MailCreateor mailCreateor;

	@Value("${app.sports.api.token}")
	private String token;

	@Value("${app.mail.host}")
	private String emailHost;

	@Value("${app.mail.port}")
	private int emailPort;

	@Value("${app.mail.password}")
	private String emailPassword;

	private static final AtomicLong lastRun = new AtomicLong(0);

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Map<BigInteger, Integer> clients = new HashMap<>();
		clients.put(BigInteger.valueOf(4), 157);
		clients.keySet().forEach(e -> {
			try {
				result.result = updateMatchdays(e, clients.get(e));
				final BigInteger id = updateLastMatch(e, clients.get(e));
				if (id != null)
					result.result += "\n" + "updateLastMatchId: " + id;
				updateResultAndNotify(e);
			} catch (final Exception ex) {
				result.exception = ex;
			}
		});
		return result;
	}

	private String updateMatchdays(final BigInteger clientId, final int teamId) throws Exception {
		if (System.currentTimeMillis() - lastRun.get() < 24 * 60 * 60 * 1000)
			return "Matchdays already run in last 24 hours";
		lastRun.set(System.currentTimeMillis());
		int count = 0;
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId +
				" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		if (repository.list(params).size() == 0) {
			JsonNode matchDays = get("https://v3.football.api-sports.io/fixtures?team=" + teamId + "&season="
					+ LocalDateTime.now().getYear());
			if (matchDays != null) {
				matchDays = matchDays.get("response");
				for (int i = 0; i < matchDays.size(); i++) {
					if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
						final ClientMarketing clientMarketing = new ClientMarketing();
						clientMarketing.setStartDate(
								new Timestamp(matchDays.get(i).get("fixture").get("timestamp").asLong() * 1000
										+ (2 * 60 * 60 * 1000)));
						clientMarketing.setEndDate(
								new Timestamp(clientMarketing.getStartDate().getTime() + (24 * 60 * 60 * 1000)));
						clientMarketing.setClientId(clientId);
						clientMarketing.setStorage(matchDays.get(i).get("fixture").get("id").asText());
						repository.save(clientMarketing);
						count++;
					}
				}
			}
		}
		return "Matchdays update: " + count;
	}

	private BigInteger updateLastMatch(final BigInteger clientId, final int teamId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch(
				"clientMarketing.startDate<='" + Instant.now() + "' and clientMarketing.endDate>'" + Instant.now() +
						"' and clientMarketing.clientId=" + clientId +
						" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		final Result list = repository.list(params);
		if (list.size() > 0) {
			JsonNode matchDay = get("https://v3.football.api-sports.io/fixtures?id="
					+ list.get(0).get("clientMarketing.storage"));
			if (matchDay != null) {
				matchDay = matchDay.get("response");
				JsonNode e = matchDay.findPath("players");
				e = e.get(e.get(0).get("team").get("id").asInt() == teamId ? 0 : 1).get("players");
				final ObjectNode poll = new ObjectMapper().createObjectNode();
				poll.put("prolog",
						"Umfrage <b>Spieler des Spiels</b> zum "
								+ matchDay.findPath("league").get("name").asText() +
								" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
								+ matchDay
										.findPath("teams").get("home").get("name").asText().replace("Munich", "München")
								+ " - "
								+ matchDay.findPath("teams").get("away").get("name").asText().replace("Munich",
										"München")
								+
								"</div>vom <b>"
								+ LocalDateTime.ofInstant(
										Instant.ofEpochMilli(
												matchDay.findPath("fixture").get("timestamp").asLong() * 1000),
										TimeZone.getTimeZone("Europe/Berlin").toZoneId())
										.format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"))
								+ "</b>. Möchtest Du teilnehmen?");
				poll.put("epilog",
						"Lieben Dank für die Teilnahme!\n\nÜbrigens, Bayern Fans treffen sich neuerdings zum gemeinsam Spiele anschauen und feiern in dieser coolen, neuen App.\n\nKlicke auf weiter und auch Du kannst mit ein paar Klicks dabei sein.");
				final ObjectNode question = poll.putArray("questions").addObject();
				question.put("question", "Wer war für Dich Spieler des Spiels?");
				final ArrayNode answers = question.putArray("answers");
				for (int i = 0; i < e.size(); i++) {
					final JsonNode statistics = e.get(i).get("statistics").get(0);
					if (!statistics.get("games").get("minutes").isNull()) {
						String s = e.get(i).get("player").get("name").asText() +
								"<explain>" + statistics.get("games").get("minutes").asInt() +
								(statistics.get("games").get("minutes").asInt() > 1 ? " gespielte Minuten"
										: " gespielte Minute");
						if (!statistics.get("goals").get("total").isNull())
							s += getLine(statistics.get("goals").get("total").asInt(), " Tor", " Tore");
						if (!statistics.get("shots").get("total").isNull())
							s += getLine(statistics.get("shots").get("total").asInt(), " Torschuss, ", " Torschüsse, ")
									+ (statistics.get("shots").get("on").isNull() ? 0
											: statistics.get("shots").get("on").asInt())
									+ " aufs Tor";
						if (!statistics.get("goals").get("assists").isNull())
							s += getLine(statistics.get("goals").get("assists").asInt(), " Assist", " Assists");
						if (!statistics.get("passes").get("total").isNull())
							s += getLine(statistics.get("passes").get("total").asInt(), " Pass, ", " Pässe, ")
									+ statistics.get("passes").get("accuracy").asInt() + " angekommen";
						if (!statistics.get("duels").get("total").isNull())
							s += getLine(statistics.get("duels").get("total").asInt(), " Duell, ", " Duelle, ")
									+ statistics.get("duels").get("won").asInt() + " gewonnen";
						if (statistics.get("cards").get("yellow").asInt() > 0
								&& statistics.get("cards").get("red").asInt() > 0)
							s += "<br/>Gelberote Karte erhalten";
						else if (statistics.get("cards").get("yellow").asInt() > 0)
							s += "<br/>Gelbe Karte erhalten";
						if (statistics.get("cards").get("red").asInt() > 0)
							s += "<br/>Rote Karte erhalten";
						s += "</explain>";
						answers.addObject().put("answer", s);
					}
				}
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
						(BigInteger) list.get(0).get("clientMarketing.id"));
				clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
				clientMarketing.setImage(Attachment.createImage(".png",
						createImagePoll(matchDay.findPath("league").get("logo").asText(),
								matchDay.findPath("teams").get("home").get("logo").asText(),
								matchDay.findPath("teams").get("away").get("logo").asText(),
								matchDay.findPath("teams").get("home").get("id").asInt() == teamId)));
				repository.save(clientMarketing);
				publish(clientMarketing);
			}
			return (BigInteger) list.get(0).get("clientMarketing.id");
		}
		return null;
	}

	private String getLine(final int x, final String singular, final String plural) {
		return "<br/>" + x + (x > 1 ? plural : singular);
	}

	private void publish(final ClientMarketing clientMarketing) throws Exception {
		// https://developers.facebook.com/docs/facebook-login/guides/access-tokens/#pagetokens
		sendNotifications(clientMarketing);
		final ImageHtmlEmail email = mailCreateor.create();
		email.setHostName(emailHost);
		email.setSmtpPort(emailPort);
		email.setCharset(StandardCharsets.UTF_8.name());
		email.setAuthenticator(new DefaultAuthenticator("support@fan-club.online", emailPassword));
		email.setSSLOnConnect(true);
		email.setFrom("support@fan-club.online");
		email.addTo("mani.afschar@jq-consulting.de");
		email.addTo("fcbayerntotal@web.de");
		email.setSubject("Survey");
		email.setTextMsg("Survey\n\nhttps://fcbayerntotal.fan-club.online/?m=" + clientMarketing.getId()
				+ "\n\nhttps://fan-club.online/med/" + Attachment.resolve(clientMarketing.getImage()));
		email.send();
	}

	private BigInteger updateResultAndNotify(final BigInteger clientId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.published=false and clientMarketing.endDate<'" + Instant.now()
				+ "' and clientMarketing.clientId=" + clientId);
		final Result list = repository.list(params);
		for (int i = 0; i < list.size(); i++) {
			final ClientMarketingResult clientMarketingResult = updateResult(
					(BigInteger) list.get(0).get("clientMarketing.id"));
			clientMarketing.setImage(Attachment.createImage(".png", createImageResult(clientMarketingResult)));
			sendNotifications(
					repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId()));
			clientMarketingResult.setPublished(true);
			repository.save(clientMarketingResult);
		}
		return null;
	}

	public synchronized ClientMarketingResult updateResult(final BigInteger clientMarketingId) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketingId);
		Result result = repository.list(params);
		final ClientMarketingResult clientMarketingResult = result.size() == 0 ? new ClientMarketingResult()
				: repository.one(ClientMarketingResult.class,
						(BigInteger) result.get(0).get("clientMarketingResult.id"));
		clientMarketingResult.setClientMarketingId(clientMarketingId);
		params.setQuery(Query.contact_listMarketing);
		params.setSearch("contactMarketing.clientMarketingId=" + clientMarketingId);
		result = repository.list(params);
		final ObjectMapper om = new ObjectMapper();
		final ObjectNode json = om.createObjectNode();
		/*
		 * for (var i = 0; i < v.storage.questions.length; i++) {
		 * var answersAverage = [], text = '', total = 0;
		 * for (var i2 = 0; i2 < v.storage.questions[i].answers.length; i2++)
		 * answersAverage.push(0);
		 * for (var i2 = 0; i2 < answers.length; i2++) {
		 * if (answers[i2]['q' + i]) {
		 * for (var i3 = 0; i3 < answers[i2]['q' + i].a.length; i3++)
		 * answersAverage[answers[i2]['q' + i].a[i3]]++;
		 */
		json.put("participants", result.size());
		json.put("finished", 0);
		for (int i2 = 0; i2 < result.size(); i2++) {
			om.readTree((String) result.get(i2).get("contactMarketing.storage")).fields()
					.forEachRemaining(e -> {
						if ("finished".equals(e.getKey())) {
							if (e.getValue().asBoolean())
								json.put("finished", json.get("finished").asInt() + 1);
						} else {
							if (!json.has(e.getKey())) {
								json.set(e.getKey(), om.createObjectNode());
								((ObjectNode) json.get(e.getKey())).set("a", om.createArrayNode());
							}
							for (int i = 0; i < e.getValue().get("a").size(); i++) {
								final int index = e.getValue().get("a").get(i).asInt();
								final ArrayNode a = ((ArrayNode) json.get(e.getKey()).get("a"));
								for (int i3 = a.size(); i3 < index; i3++)
									a.add(0);
								a.set(index, a.get(index).asInt() + 1);
							}
							if (e.getValue().has("t") && !Strings.isEmpty(e.getValue().get("t").asText())) {
								final ObjectNode o = (ObjectNode) json.get(e.getKey());
								if (!o.has("t"))
									o.put("t", "");
								o.put("t", o.get("t").asText() +
										"<div>" + e.getValue().get("t").asText() + "</div>");
							}
						}
					});
		}
		clientMarketingResult.setStorage(om.writeValueAsString(json));
		repository.save(clientMarketingResult);
		return clientMarketingResult;
	}

	private void sendNotifications(final ClientMarketing clientMarketing) throws Exception {
		final QueryParams params;
		if (clientMarketing.getEndDate().getTime() < Instant.now().getEpochSecond() * 1000) {
			params = new QueryParams(Query.contact_listMarketing);
			params.setSearch(
					"contactMarketing.finished=true and contactMarketing.clientMarketingId=" + clientMarketing.getId());
		} else {
			params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.verified=true and contact.clientId=" + clientMarketing.getClientId());
		}
		final Result users = repository.list(params);
		final ContactNotificationTextType type = params.getQuery() == Query.contact_listId
				? ContactNotificationTextType.clientMarketing
				: ContactNotificationTextType.clientMarketingResult;
		for (int i2 = 0; i2 < users.size(); i2++)
			notificationService.sendNotification(null,
					repository.one(Contact.class,
							(BigInteger) users.get(i2)
									.get(params.getQuery() == Query.contact_listId ? "contact.id"
											: "contactMarketing.contactId")),
					type, "m=" + clientMarketing.getId());
	}

	protected JsonNode get(final String url) {
		return WebClient
				.create(url)
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(JsonNode.class).block().getBody();
	}

	private byte[] createImagePoll(final String urlLeague, final String urlHome, final String urlAway,
			final boolean homeMatch) throws Exception {
		final Font customFont = Font
				.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
				.deriveFont(66f);
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
		final int width = 800, height = 500, padding = 30;
		final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = output.createGraphics();
		g2.setComposite(AlphaComposite.Src);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final RadialGradientPaint gradient = new RadialGradientPaint(width / 2 - 2 * padding, height / 2 - 2 * padding,
				height,
				new float[] { .3f, 1f },
				new Color[] { new Color(245, 239, 232), new Color(246, 194, 166) });
		g2.setPaint(gradient);
		g2.fill(new Rectangle2D.Float(0, 0, width, height));
		g2.setComposite(AlphaComposite.SrcAtop);
		if (!homeMatch)
			drawImage(urlHome, g2, width / 2, padding, height / 2, -1);
		drawImage(urlAway, g2, width / 2, padding, height / 2, 1);
		if (homeMatch)
			drawImage(urlHome, g2, width / 2, padding, height / 2, -1);
		drawImage(urlLeague, g2, width - padding, padding, height / 6, 0);
		g2.setFont(customFont);
		g2.setColor(Color.BLACK);
		String s = "Umfrage";
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 15);
		g2.setFont(customFont.deriveFont(40f));
		s = "Spieler des Spiels";
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
		// final BufferedImageTranscoder imageTranscoder = new
		// BufferedImageTranscoder();
		// imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width);
		// imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);
		// final TranscoderInput input = new TranscoderInput(svgFile);
		// imageTranscoder.transcode(input, null);
		// g2.drawImage(imageTranscoder.getBufferedImage(), 770 - image.getWidth(), 470
		// - image.getHeight(), null);
		g2.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(output, "png", out);
		return out.toByteArray();
	}

	private byte[] createImageResult(final ClientMarketingResult clientMarketingResult) throws Exception {
		final Font customFont = Font
				.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
				.deriveFont(66f);
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
		final int width = 800, height = 500, padding = 30;
		final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = output.createGraphics();
		g2.setComposite(AlphaComposite.Src);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		final RadialGradientPaint gradient = new RadialGradientPaint(width / 2 - 2 * padding, height / 2 - 2 * padding,
				height,
				new float[] { .3f, 1f },
				new Color[] { new Color(245, 239, 232), new Color(246, 194, 166) });
		g2.setPaint(gradient);
		g2.fill(new Rectangle2D.Float(0, 0, width, height));
		g2.setComposite(AlphaComposite.SrcAtop);
		if (!homeMatch)
			drawImage(urlHome, g2, width / 2, padding, height / 2, -1);
		drawImage(urlAway, g2, width / 2, padding, height / 2, 1);
		if (homeMatch)
			drawImage(urlHome, g2, width / 2, padding, height / 2, -1);
		drawImage(urlLeague, g2, width - padding, padding, height / 6, 0);
		g2.setFont(customFont);
		g2.setColor(Color.BLACK);
		String s = "Umfrage";
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 15);
		g2.setFont(customFont.deriveFont(40f));
		s = "Spieler des Spiels";
		g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
		g2.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(output, "png", out);
		return out.toByteArray();
	}

	private void drawImage(final String url, final Graphics2D g, final int x, final int y, final int height,
			final int pos) throws Exception {
		final BufferedImage image = ImageIO.read(new URL(url).openStream());
		final int paddingLogos = -10;
		final int w = image.getWidth() / image.getHeight() * height;
		g.drawImage(image, x - (pos == 1 ? 0 : w) + pos * paddingLogos, y,
				x + (pos == 1 ? w : 0) + pos * paddingLogos,
				height + y, 0, 0, image.getWidth(), image.getHeight(), null);
	}
}
