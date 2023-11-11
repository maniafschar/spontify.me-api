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
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService.MailCreateor;

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

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
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sync");
		try {
			result.result = updateMatchdays();
			final BigInteger id = updateLastMatch();
			if (id != null)
				result.result += "\n" + "updateLastMatchId: " + id;
		} catch (final Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private String updateMatchdays() throws Exception {
		if (System.currentTimeMillis() - lastRun.get() < 24 * 60 * 60 * 1000)
			return "Matchdays already run in last 24 hours";
		lastRun.set(System.currentTimeMillis());
		int count = 0;
		final BigInteger clientId = BigInteger.valueOf(4);
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId +
				" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		if (repository.list(params).size() == 0) {
			JsonNode matchDays = get("https://v3.football.api-sports.io/fixtures?team=157&season="
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

	private BigInteger updateLastMatch() throws Exception {
		final BigInteger clientId = BigInteger.valueOf(4);
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
				e = e.get(e.get(0).get("team").get("id").asInt() == 157 ? 0 : 1).get("players");
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
						createImage(matchDay.findPath("league").get("logo").asText(),
								matchDay.findPath("teams").get("home").get("logo").asText(),
								matchDay.findPath("teams").get("away").get("logo").asText(),
								matchDay.findPath("teams").get("home").get("id").asInt() == 157)));
				repository.save(clientMarketing);
				publish(Attachment.resolve(clientMarketing.getImage()));
			}
			return (BigInteger) list.get(0).get("clientMarketing.id");
		}
		return null;
	}

	private String getLine(final int x, final String singular, final String plural) {
		return "<br/>" + x + (x > 1 ? plural : singular);
	}

	private void publish(final String image) throws Exception {
		// https://developers.facebook.com/docs/facebook-login/guides/access-tokens/#pagetokens
		final ImageHtmlEmail email = mailCreateor.create();
		email.setHostName(emailHost);
		email.setSmtpPort(emailPort);
		email.setCharset(StandardCharsets.UTF_8.name());
		email.setAuthenticator(new DefaultAuthenticator("support@fan-club.online", emailPassword));
		email.setSSLOnConnect(true);
		email.setFrom("support@fan-club.online");
		email.addTo("mani.afschar@jq-consulting.de");
		email.setSubject("Survey");
		email.setTextMsg("Survey");
		email.attach(new URL("https://fan-club.online/med/" + image), "pic", "pic");
		email.send();
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

	private byte[] createImage(final String urlLeague, final String urlHome, final String urlAway,
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
