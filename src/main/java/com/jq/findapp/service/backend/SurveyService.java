package com.jq.findapp.service.backend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.batik.transcoder.TranscoderInput;
import org.apache.commons.io.IOUtils;
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

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

	@Value("${app.sports.api.token}")
	private String token;

	private static final AtomicLong lastRun = new AtomicLong(0);

	public SchedulerResult sync() throws Exception {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sync");
		result.result = syncMatchdays();
		final BigInteger id = syncLastMatch();
		if (id != null)
			result.result += "\n" + "syncLastMatchId: " + id;
		return result;
	}

	private String syncMatchdays() throws Exception {
		if (System.currentTimeMillis() - lastRun.get() < 24 * 60 * 60 * 1000)
			return "Matchdays already run in last 24 hours";
		lastRun.set(System.currentTimeMillis());
		int count = 0;
		final BigInteger clientId = BigInteger.valueOf(4);
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch("clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId +
				" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		if (repository.list(params).size() == 0) {
			final JsonNode matchDays = get("https://v3.football.api-sports.io/fixtures?team=157&season="
					+ LocalDateTime.now().getYear());
			if (matchDays != null) {
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

	private BigInteger syncLastMatch() {
		final BigInteger clientId = BigInteger.valueOf(4);
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setSearch(
				"clientMarketing.startDate<='" + Instant.now() + "' and clientMarketing.endDate>'" + Instant.now() +
						"' and clientMarketing.clientId=" + clientId +
						" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
		final Result list = repository.list(params);
		if (list.size() > 0) {
			final JsonNode matchDay = get("https://v3.football.api-sports.io/fixtures?id="
					+ list.get(0).get("clientMarketing.storage"));
			if (matchDay != null) {
				JsonNode e = matchDay.findPath("players");
				e = e.get(e.get(0).get("team").get("id").asInt() == 157 ? 0 : 1).get("players");
				final ObjectNode poll = new ObjectNode();
				poll.put("prolog", "Möchtest Du an der Umfrage Spieler des Spiels zum " + matchDay.findPath("league").get("name").asText() +
					" Spiel " + matchDay.findPath("teams").get("home").asText() + " - " + matchDay.findPath("teams").get("away").asText() + 
					" am " + new SimpleDateFormatter("d.M.yyyy H:mm").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(matchDay.findPath("fixture").get("timestamp").asLong()),
					TimeZone.getTimeZone("Europe/Berlin").toZoneId())) + " teilnehmen?");
				poll.put("epilog", "Lieben Dank für die Teilnahme!\n\nÜbrigens, Bayern Fans treffen sich neuerdings zum gemeinsam Spiele anschauen und feiern in dieser coolen, neuen App.\n\nKlicke auf weiter und auch Du kannst mit ein paar Klicks dabei sein.");
				final ObjectNode question = poll.putArray("questions").addObject();
				question.put("question", "Wer war für Dich Spieler des Spiels?");
				final ArrayNode answers = question.addArray("answers");
				for (int i = 0; i < e.size(); i++) {
					final JsonNode statistics = e.get(i).get("statistics").get(0);
					if (!statistics.get("games").get("minutes").isNull()) {
						String s = e.get(i).get("player").get("name").asText() +
								"<desc>Gespielte Minuten: " + statistics.get("games").get("minutes").asInt();
						if (!statistics.get("shots").get("total").isNull())
							s += "<br/>Torschüsse ingesamt/aufs Tor: " + statistics.get("shots").get("total").asInt()
									+ "/" + statistics.get("shots").get("on").asInt();
						if (!statistics.get("goals").get("total").isNull()
								|| !statistics.get("goals").get("assists").isNull())
							s += "<br/>Tore/Assists: " + statistics.get("goals").get("total").asInt() + "/"
									+ statistics.get("goals").get("assists").asInt();
						if (!statistics.get("passes").get("total").isNull())
							s += "<br/>Pässe/davon angekommen: " + statistics.get("passes").get("total").asInt() + "/"
									+ statistics.get("passes").get("accuracy").asInt();
						if (!statistics.get("duels").get("total").isNull())
							s += "<br/>Duelle/davon gewonnen: " + statistics.get("duels").get("total").asInt() + "/"
									+ statistics.get("duels").get("won").asInt();
						if (!statistics.get("cards").get("yellow").isNull()
								|| !statistics.get("cards").get("red").isNull())
							s += "<br/>Gelb/Rot: " + statistics.get("cards").get("yellow").asInt() + "/"
									+ statistics.get("cards").get("red").asInt();
						s += "</desc>";
						answers.addObject().put("answer", s);
					}
				}
				final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, (BigInteger) list.get(0).get("clientMarketing.id"));
				clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
				repository.save(clientMarketing);
				createImage(matchDay.findPath("league").get("logo").asText(),
					    matchDay.findPath("teams").get("home").get("logo").asText(),
					    matchDay.findPath("teams").get("away").get("logo").asText());
			}
			return (BigInteger) list.get(0).get("clientMarketing.id");
		}
		return null;
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

	private void createImage(String urlLeague, String urlHome, String urlAway) throws IOException {
		final Font customFont = Font.createFont(Font.TRUETYPE_FONT, new File("custom_font.ttf")).deriveFont(36f);
		GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
		final BufferedImage output = new BufferedImage(800, 500, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g2 = output.createGraphics();
		g2.setComposite(AlphaComposite.Src);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Color startColor = Color.red;
		Color endColor = Color.blue;
		GradientPaint gradient = new GradientPaint(0, 0, startColor, 800, 500, endColor);
		g2.setPaint(gradient);
		g2.fill(new Rectangle2D.Float(0, 0, 800, 500));
		g2.setComposite(AlphaComposite.SrcAtop);
		BufferedImage image = ImageIO.read(getClass().getResourceAsStream("/157.png"));
		g2.drawImage(image, 30, 30, null);
		g2.drawImage(image, 770 - image.getWidth(), 30, null);
		g2.drawImage(image, 770 - image.getWidth(), 470 - image.getHeight(), null);
		g2.setFont(customFont);
		g2.setColor(Color.BLACK);
		g2.drawString(":", 398, 100);
		g2.drawString("Umfrage", 30, 300);
		g2.setFont(customFont.deriveFont(18f));
		g2.drawString("Spieler des Spiels", 30, 400);
		BufferedImageTranscoder imageTranscoder = new BufferedImageTranscoder();
		imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, width);
		imageTranscoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height);
		TranscoderInput input = new TranscoderInput(svgFile);
		imageTranscoder.transcode(input, null);
		g2.drawImage(imageTranscoder.getBufferedImage(), 770 - image.getWidth(), 470 - image.getHeight(), null);
		g2.dispose();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(output, "png", out);
		IOUtils.write(out.toByteArray(), new FileOutputStream("test.png"));
	}
}
