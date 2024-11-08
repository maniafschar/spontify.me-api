package com.jq.findapp.service.backend;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.batik.ext.awt.RadialGradientPaint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.Answer;
import com.jq.findapp.entity.ClientMarketing.Poll;
import com.jq.findapp.entity.ClientMarketing.Question;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.ClientMarketingResult.PollResult;
import com.jq.findapp.entity.Event.FutureEvent;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.service.backend.CronService.CronResult;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text.TextId;

@Service
public class MatchDayService {
	final Synchonize synchronize = new Synchonize();
	private final Image image = new Image();

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MarketingService marketingService;

	@Value("${app.sports.api.token}")
	private String token;

	private static volatile long pauseUntil = 0;
	private static final String STORAGE_PREFIX = "api-sports-";

	static class PollMatchDay extends Poll {
		public String home;
		public String away;
		public String homeName;
		public String awayName;
		public int homeId;
		public int awayId;
		public String league;
		public String leagueName;
		public long timestamp;
		public String venue;
		public String city;
		public String location;
		public List<String> matches = new ArrayList<>();
		public List<Map<String, Object>> statistics = new ArrayList<>();
	}

	class Synchonize {
		private final static int FIRST_YEAR = 2010;

		BigInteger prediction(final BigInteger clientId, final int teamId) throws Exception {
			final long now = Instant.now().getEpochSecond();
			final JsonNode json = get("team=" + teamId + "&season=" + currentSeason());
			for (int i = 0; i < json.size(); i++) {
				if ("NS".equals(json.get(i).get("fixture").get("status").get("short").asText())) {
					final Instant date = Instant
							.ofEpochSecond(json.get(i).get("fixture").get("timestamp").asLong());
					if (date.getEpochSecond() >= now && date.minus(Duration.ofDays(1)).getEpochSecond() < now) {
						final PollMatchDay poll = new PollMatchDay();
						poll.type = "Prediction";
						poll.home = json.get(i).get("teams").get("home").get("logo").asText();
						poll.away = json.get(i).get("teams").get("away").get("logo").asText();
						poll.homeName = json.get(i).get("teams").get("home").get("name").asText()
								.replace("Munich", "München");
						poll.awayName = json.get(i).get("teams").get("away").get("name").asText()
								.replace("Munich", "München");
						poll.homeId = json.get(i).get("teams").get("home").get("id").asInt();
						poll.awayId = json.get(i).get("teams").get("away").get("id").asInt();
						poll.league = json.get(i).get("league").get("logo").asText();
						poll.leagueName = json.get(i).get("league").get("name").asText();
						poll.timestamp = json.get(i).get("fixture").get("timestamp").asLong();
						poll.venue = json.get(i).get("fixture").get("venue").get("name").asText();
						poll.city = json.get(i).findPath("fixture").get("venue").get("city").asText();
						poll.location = json.get(i).get("teams").get("home").get("id").asInt() == teamId ? "home"
								: "away";
						poll.subject = poll.homeName + " : " + poll.awayName + " (" + poll.city + " · " + poll.venue
								+ " · " + formatDate(poll.timestamp, null) + ")";
						poll.textId = TextId.notification_clientMarketingPollPrediction;
						final Instant end = Instant.ofEpochSecond(poll.timestamp)
								.minus(Duration.ofMinutes(15));
						final QueryParams params = new QueryParams(Query.misc_listMarketing);
						params.setSearch(
								"clientMarketing.endDate=cast('" + end + "' as timestamp) and clientMarketing.clientId="
										+ clientId);
						if (repository.list(params).size() == 0) {
							predictionAddStatistics(clientId, poll);
							final ClientMarketing clientMarketing = new ClientMarketing();
							clientMarketing.setCreateResult(true);
							clientMarketing.setClientId(clientId);
							clientMarketing.setSkills("9." + teamId);
							clientMarketing.setStartDate(new Timestamp(end
									.minus(Duration.ofDays(1)).toEpochMilli()));
							clientMarketing.setEndDate(new Timestamp(end.toEpochMilli()));
							clientMarketing.setStorage(Json.toString(poll));
							clientMarketing.setImage(Attachment.createImage(".png",
									image.create(poll, "Ergebnistipps",
											repository.one(Client.class, clientId), null)));
							repository.save(clientMarketing);
							return clientMarketing.getId();
						}
					}
				}
			}
			return null;
		}

		private void predictionAddStatistics(final BigInteger clientId, final PollMatchDay poll) throws Exception {
			final Question question = new Question();
			question.question = "Erzielen wir eines der letzten Ergebnisse?";
			question.textField = "text";
			poll.questions.add(question);
			final int teamId = poll.location == "away" ? poll.awayId : poll.homeId;
			final List<Map<String, Object>> matches = new ArrayList<>();
			for (int i = FIRST_YEAR; i <= currentSeason(); i++) {
				final JsonNode json = get("team=" + teamId + "&season=" + i);
				for (int i2 = 0; i2 < json.size(); i2++) {
					if (json.get(i2).get("teams").get("home").get("id").asInt() == poll.homeId
							&& json.get(i2).get("teams").get("away").get("id").asInt() == poll.awayId
							&& "FT".equals(json.get(i2).get("fixture").get("status").get("short").asText())) {
						final JsonNode fixture = get("id=" + json.get(i2).get("fixture").get("id").asInt()).get(0);
						if (fixture != null && fixture.has("statistics") && fixture.get("statistics").size() > 1
								&& fixture.get("statistics").get(0).has("statistics")) {
							final Map<String, Object> match = new HashMap<>();
							match.put("timestamp", fixture.get("fixture").get("timestamp").asLong());
							match.put("goals", fixture.get("goals"));
							match.put("statistics", fixture.get("statistics"));
							matches.add(match);
							if (matches.size() > 7)
								break;
						}
					}
				}
			}
			final Map<String, Double> homeList = new HashMap<>();
			final Map<String, Double> awayList = new HashMap<>();
			final List<String> labels = new ArrayList<>();
			String added = "|";
			for (int i = 0; i < matches.size(); i++) {
				final JsonNode json = (JsonNode) matches.get(i).get("statistics");
				for (int i2 = 0; i2 < json.get(0).get("statistics").size(); i2++) {
					final String label = json.get(0).get("statistics").get(i2).get("type").asText();
					if (!homeList.containsKey(label)) {
						homeList.put(label, 0.0);
						awayList.put(label, 0.0);
						labels.add(label);
					}
					homeList.put(label,
							homeList.get(label) + json.get(0).get("statistics").get(i2).get("value").asDouble());
					awayList.put(label,
							awayList.get(label) + json.get(1).get("statistics").get(i2).get("value").asDouble());
				}
				final String s = ((JsonNode) matches.get(i).get("goals")).get("home").intValue() + " : "
						+ ((JsonNode) matches.get(i).get("goals")).get("away").intValue();
				if (!added.contains("|" + s + "|")) {
					final Answer answer = new Answer();
					answer.answer = s;
					poll.questions.get(0).answers.add(answer);
					added += s + "|";
				}
				if (i < 8)
					poll.matches.add(s + "|"
							+ formatDate((Long) matches.get(i).get("timestamp"), "d.M.yyyy H:mm"));
			}
			final List<Map<String, Object>> statistics = new ArrayList<>();
			for (int i = 0; i < labels.size(); i++) {
				final Map<String, Object> row = new HashMap<>();
				row.put("home", homeList.get(labels.get(i)) / matches.size());
				row.put("away", awayList.get(labels.get(i)) / matches.size());
				row.put("label", labels.get(i));
				statistics.add(row);
			}
			String tips = "1 : 1|2 : 1|1 : 0|2 : 0|0 : 0|2 : 2|3 : 1|0 : 1|1 : 2";
			for (int i = poll.questions.get(0).answers.size(); i < 8; i++) {
				final String s = tips.substring(0, tips.indexOf('|'));
				tips = tips.substring(tips.indexOf('|') + 1);
				if (!added.contains("|" + s + "|")) {
					final Answer answer = new Answer();
					answer.answer = s;
					poll.questions.get(0).answers.add(answer);
				}
			}
			poll.statistics = statistics;
			final Answer answer = new Answer();
			answer.answer = "Oder erzielen wir ein anderes Ergebnis?";
			poll.questions.get(0).answers.add(answer);
			poll.prolog = "<b>Ergebnistipps</b> zum "
					+ poll.leagueName
					+ " Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
					+ poll.homeName
					+ " - "
					+ poll.awayName
					+ "</div>vom <b>"
					+ formatDate(poll.timestamp, null)
					+ "</b>. Möchtest Du teilnehmen?";
			poll.epilog = "Lieben Dank für die Teilnahme!\nDas Ergebnis wird kurz vor dem Spiel hier bekanntgegeben.\n\nLust auf mehr <b>Fan Feeling</b>? In unserer neuen App bauen wir eine neue <b>Fußball Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.";
		}

		BigInteger playerOfTheMatch(final BigInteger clientId, final int teamId) throws Exception {
			final JsonNode matchDays = get("team=" + teamId + "&season=" + currentSeason());
			if (matchDays != null) {
				for (int i = 0; i < matchDays.size(); i++) {
					if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
						final Instant startDate = Instant
								.ofEpochSecond(matchDays.get(i).get("fixture").get("timestamp").asLong());
						if (startDate.plus(Duration.ofHours(8)).isAfter(Instant.now())
								&& startDate.plus(Duration.ofHours(2)).isBefore(Instant.now())) {
							final QueryParams params = new QueryParams(Query.misc_listMarketing);
							params.setSearch(
									"clientMarketing.startDate=cast('" + startDate
											+ "' as timestamp) and clientMarketing.clientId=" + clientId);
							if (repository.list(params).size() > 0)
								break;
							final JsonNode matchDay = get("id=" + matchDays.get(i).get("fixture").get("id").asText())
									.get(0);
							if (matchDay != null && matchDay.findPath("players").get(0) != null) {
								JsonNode players = matchDay.findPath("players");
								players = players.get(players.get(0).get("team").get("id").asInt() == teamId ? 0 : 1)
										.get("players");
								final PollMatchDay poll = new PollMatchDay();
								poll.type = "PlayerOfTheMatch";
								poll.home = matchDay.findPath("home").get("logo").asText();
								poll.away = matchDay.findPath("away").get("logo").asText();
								poll.homeName = matchDay.findPath("home").get("name").asText().replace("Munich",
										"München");
								poll.awayName = matchDay.findPath("away").get("name").asText().replace("Munich",
										"München");
								poll.homeId = matchDay.findPath("home").get("id").asInt();
								poll.awayId = matchDay.findPath("away").get("id").asInt();
								poll.league = matchDay.findPath("league").get("logo").asText();
								poll.timestamp = matchDay.findPath("fixture").get("timestamp").asLong();
								poll.venue = matchDay.findPath("fixture").get("venue").get("name").asText();
								poll.city = matchDay.findPath("fixture").get("venue").get("city").asText();
								poll.textId = TextId.notification_clientMarketingPollPlayerOfTheMath;
								poll.location = matchDay.findPath("teams").get("home").get("id").asInt() == teamId
										? "home"
										: "away";
								poll.prolog = "Umfrage <b>Spieler des Spiels</b> zum "
										+ matchDay.findPath("league").get("name").asText() +
										" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
										+ poll.homeName + " - " + poll.awayName
										+ "</div>vom <b>"
										+ formatDate(matchDay.findPath("fixture").get("timestamp").asLong(),
												null)
										+ "</b>. Möchtest Du teilnehmen?";
								poll.subject = poll.homeName + " : " + poll.awayName + " (" + poll.city + " · "
										+ poll.venue + " · " + formatDate(poll.timestamp, null) + ")";
								final Question question = new Question();
								question.question = "Wer war für Dich Spieler des Spiels?";
								playerOfTheMatchAddAnswers(question.answers, players);
								poll.questions.add(question);
								final ClientMarketing clientMarketing = new ClientMarketing();
								clientMarketing.setSkills("9." + teamId);
								clientMarketing.setCreateResult(true);
								clientMarketing.setStartDate(new Timestamp(startDate.toEpochMilli()));
								clientMarketing
										.setEndDate(new Timestamp(startDate.plus(Duration.ofHours(18)).toEpochMilli()));
								final String endDate = formatDate(
										clientMarketing.getEndDate().getTime() / 1000 + 10 * 60, null);
								clientMarketing.setClientId(clientId);
								clientMarketing.setImage(Attachment.createImage(".png",
										image.create(poll, "Spieler", repository.one(Client.class, clientId), null)));
								poll.epilog = "Lieben Dank für die Teilnahme!\nDas Ergebnis wird am " + endDate
										+ " hier bekanntgegeben.\n\nLust auf mehr <b>Fan Feeling</b>? In unserer neuen App bauen wir eine neue <b>Fußball Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.";
								clientMarketing.setStorage(Json.toString(poll));
								repository.save(clientMarketing);
								return clientMarketing.getId();
							}
						}
					}
				}
			}
			return null;
		}

		private void playerOfTheMatchAddAnswers(final List<Answer> answers, final JsonNode players) {
			for (int i = 0; i < players.size(); i++) {
				final JsonNode statistics = players.get(i).get("statistics").get(0);
				if (!statistics.get("games").get("minutes").isNull()) {
					String s = players.get(i).get("player").get("name").asText() +
							"<explain>" + statistics.get("games").get("minutes").asInt() +
							(statistics.get("games").get("minutes").asInt() > 1 ? " gespielte Minuten"
									: " gespielte Minute");
					if (!statistics.get("goals").get("total").isNull())
						s += getLine(statistics.get("goals").get("total").asInt(), " Tor", " Tore");
					if (!statistics.get("shots").get("total").isNull())
						s += getLine(statistics.get("shots").get("total").asInt(), " Torschuss, ",
								" Torschüsse, ")
								+ (statistics.get("shots").get("on").isNull() ? 0
										: statistics.get("shots").get("on").asInt())
								+ " aufs Tor";
					if (!statistics.get("goals").get("assists").isNull())
						s += getLine(statistics.get("goals").get("assists").asInt(), " Assist",
								" Assists");
					if (!statistics.get("passes").get("total").isNull())
						s += getLine(statistics.get("passes").get("total").asInt(), " Pass, ",
								" Pässe, ")
								+ statistics.get("passes").get("accuracy").asInt() + " angekommen";
					if (!statistics.get("duels").get("total").isNull())
						s += getLine(statistics.get("duels").get("total").asInt(), " Duell, ",
								" Duelle, ")
								+ statistics.get("duels").get("won").asInt() + " gewonnen";
					if (statistics.get("cards").get("yellow").asInt() > 0
							&& statistics.get("cards").get("red").asInt() > 0)
						s += "<br/>Gelberote Karte erhalten";
					else if (statistics.get("cards").get("yellow").asInt() > 0)
						s += "<br/>Gelbe Karte erhalten";
					if (statistics.get("cards").get("red").asInt() > 0)
						s += "<br/>Rote Karte erhalten";
					s += "</explain>";
					final Answer answer = new Answer();
					answer.answer = s;
					answers.add(answer);
				}
			}
		}

		String result(final BigInteger clientId) throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch(
					"clientMarketingResult.published=false and clientMarketing.endDate<=cast('" + Instant.now()
							+ "' as timestamp) and clientMarketing.clientId=" + clientId);
			final Result list = repository.list(params);
			String result = "";
			for (int i = 0; i < list.size(); i++) {
				final ClientMarketingResult clientMarketingResult = marketingService.synchronizeResult(
						(BigInteger) list.get(i).get("clientMarketing.id"));
				final PollMatchDay poll = Json.toObject(Attachment.resolve(
						repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId())
								.getStorage()),
						PollMatchDay.class);
				if (Json.toObject(Attachment.resolve(clientMarketingResult.getStorage()),
						PollResult.class).answers.size() > 0) {
					String prefix;
					if ("Prediction".equals(poll.type))
						prefix = "Ergebnistipps";
					else
						prefix = "Spieler";
					clientMarketingResult.setImage(Attachment.createImage(".png",
							image.create(poll, prefix, repository.one(Client.class, clientId), clientMarketingResult)));
					repository.save(clientMarketingResult);
					if ("PlayerOfTheMatch".equals(poll.type))
						prefix = "Umfrage Spieler des Spiels";
					result += clientMarketingResult.getId() + " ";
				} else
					repository.save(clientMarketingResult);
			}
			return result.trim();
		}

		private String updateMatchdays(final BigInteger clientId) throws Exception {
			final JsonNode json = Json.toNode(Attachment.resolve(repository.one(Client.class, clientId).getStorage()));
			String result = "";
			if (json.has("matchDays")) {
				for (int i2 = 0; i2 < json.get("matchDays").size(); i2++) {
					final String label = "team=" + json.get("matchDays").get(i2).asInt() + "&season=" + currentSeason();
					final JsonNode matchDays = get(label);
					if (matchDays != null) {
						final QueryParams params = new QueryParams(Query.misc_listStorage);
						for (int i = 0; i < matchDays.size(); i++) {
							if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())
									&& matchDays.get(i).get("fixture").get("timestamp")
											.asLong() > System.currentTimeMillis() / 1000
									&& Instant.ofEpochSecond(
											matchDays.get(i).get("fixture").get("timestamp").asLong())
											.plus(Duration.ofHours(6)).isBefore(Instant.now())) {
								params.setSearch("storage.label='" + STORAGE_PREFIX + label + "'");
								final Result list = repository.list(params);
								if (list.size() > 0) {
									final Storage storage = repository.one(Storage.class,
											(BigInteger) list.get(0).get("storage.id"));
									storage.setStorage("");
									repository.save(storage);
									get(label);
								}
								result += "|" + json.get("matchDays").get(i2).asInt();
								break;
							}
						}
					}
				}
			}
			return result.length() > 0 ? "\nmatchdays: " + result.substring(1) : result;
		}

		private String getLine(final int x, final String singular, final String plural) {
			return "<br/>" + x + (x > 1 ? plural : singular);
		}
	}

	private class Image {
		private final int width = 600, height = 315, padding = 20;

		private byte[] create(final PollMatchDay poll, final String subtitlePrefix, final Client client,
				final ClientMarketingResult clientMarketingResult) throws Exception {
			final JsonNode json = Json.toNode(Attachment.resolve(client.getStorage())).get("css");
			final String[] color1 = json.get("bg1stop").asText().replace("rgb(", "").replace(")", "").split(",");
			final String[] color2 = json.get("bg1start").asText().replace("rgb(", "").replace(")", "").split(",");
			final String[] color3 = json.get("text").asText().replace("rgb(", "").replace(")", "").split(",");
			final String urlLeague = poll.league;
			final String urlHome = poll.home;
			final String urlAway = poll.away;
			final boolean homeMatch = "home".equals(poll.location);
			final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			final Color colorText = new Color(Integer.valueOf(color3[0].trim()), Integer.valueOf(color3[1].trim()),
					Integer.valueOf(color3[2].trim()));
			final Graphics2D g2 = output.createGraphics();
			g2.setComposite(AlphaComposite.Src);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final RadialGradientPaint gradient = new RadialGradientPaint(width / 2 - 2 * padding,
					height / 2 - 2 * padding,
					height,
					new float[] { .3f, 1f },
					new Color[] {
							new Color(Integer.valueOf(color1[0].trim()), Integer.valueOf(color1[1].trim()),
									Integer.valueOf(color1[2].trim())),
							new Color(Integer.valueOf(color2[0].trim()), Integer.valueOf(color2[1].trim()),
									Integer.valueOf(color2[2].trim()))
					});
			g2.setPaint(gradient);
			g2.fill(new Rectangle2D.Float(0, 0, width, height));
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
					clientMarketingResult == null && "PlayerOfTheMatch".equals(poll.type) ? 1 : 0.15f));
			final int h = (int) (height * 0.4);
			if (!homeMatch)
				draw(urlHome, g2, width / 2, padding, h, -1);
			draw(urlAway, g2, width / 2, padding, h, 1);
			if (homeMatch)
				draw(urlHome, g2, width / 2, padding, h, -1);
			draw(urlLeague, g2, width - padding, padding, height / 4, 0);
			try (final InputStream in = getClass().getResourceAsStream("/Comfortaa-Regular.ttf")) {
				final Font customFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(50f);
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
				g2.setFont(customFont);
				g2.setColor(colorText);
				String s = "Umfrage";
				if (clientMarketingResult != null)
					s += "ergebnis";
				g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 14.5f);
				g2.setFont(customFont.deriveFont(24f));
				s = subtitlePrefix + " des Spiels vom " + formatDate(poll.timestamp, "d.M.yyyy H:mm");
				g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));
				g2.setFont(customFont.deriveFont(12f));
				s = "© " + LocalDateTime.now().getYear() + " " + client.getName();
				g2.drawString(s, width - g2.getFontMetrics().stringWidth(s) - padding, height - padding);
				if (clientMarketingResult != null)
					result(g2, customFont, poll,
							Json.toObject(Attachment.resolve(clientMarketingResult.getStorage()), PollResult.class),
							colorText);
				else if ("Prediction".equals(poll.type))
					prediction(g2, customFont, poll, colorText, client.getId());
				// TODO final BufferedImageTranscoder imageTranscoder = new
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
		}

		private void prediction(final Graphics2D g2, final Font customFont, final PollMatchDay poll,
				final Color colorText, final BigInteger clientId) throws Exception {
			g2.setFont(customFont.deriveFont(12f));
			final int h = g2.getFontMetrics().getHeight();
			int y = padding;
			final double w = width * 0.3, delta = 1.6;
			for (int i = 0; i < poll.matches.size(); i++) {
				final String[] s = poll.matches.get(poll.matches.size() - i - 1).split("\\|");
				g2.drawString(s[0], width - padding - 120 - g2.getFontMetrics().stringWidth(s[0]) / 2, y + h);
				g2.drawString(s[1], width - padding - g2.getFontMetrics().stringWidth(s[1]), y + h);
				y += delta * padding;
			}
			y = padding;
			if (poll.statistics.size() == 0) {
				final String[] s = new String[4];
				s[0] = "Das erste Spiel seit langem zwischen";
				s[1] = poll.homeName + " : " + poll.awayName;
				s[2] = "in " + poll.city + " · " + poll.venue;
				s[3] = "Was meinst Du, wie geht das Spiel aus?";
				g2.setFont(customFont.deriveFont(24f));
				final double pad = g2.getFontMetrics().getHeight() * 1.8;
				y += pad;
				g2.drawString(s[0], (width - g2.getFontMetrics().stringWidth(s[0])) / 2, y + h);
				y += pad;
				g2.drawString(s[1], (width - g2.getFontMetrics().stringWidth(s[1])) / 2, y + h);
				y += pad;
				g2.drawString(s[2], (width - g2.getFontMetrics().stringWidth(s[2])) / 2, y + h);
				y += pad;
				g2.drawString(s[3], (width - g2.getFontMetrics().stringWidth(s[3])) / 2, y + h);
			} else {
				double max = 0;
				for (int i = 0; i < poll.statistics.size() && i < 9; i++) {
					if (!poll.statistics.get(i).get("label").toString().toLowerCase().contains("passes")) {
						max = Math.max(max, (double) poll.statistics.get(i).get("home"));
						max = Math.max(max, (double) poll.statistics.get(i).get("away"));
					}
				}
				final Map<String, String> labels = new HashMap<>();
				labels.put("Shots on Goal", "Schüsse auf das Tor");
				labels.put("Shots off Goal", "Schüsse neben das Tor");
				labels.put("Total Shots", "Schüsse insgesamt");
				labels.put("Blocked Shots", "Geblockte Schüsse");
				labels.put("Shots insidebox", "Schüsse innerhalb des 16er");
				labels.put("Shots outsidebox", "Schüsse außerhalb des 16er");
				labels.put("Fouls", "Fouls");
				labels.put("Corner Kicks", "Ecken");
				labels.put("Offsides", "Abseits");
				for (int i = 0; i < poll.statistics.size() && i < 9; i++) {
					final double w2 = ((double) poll.statistics.get(i).get("home")) / max * w;
					g2.setColor(new Color(255, 100, 0, 50));
					g2.fillRect(padding + (int) (w - w2), y - 4, (int) w2, h * 2);
					g2.setColor(new Color(0, 100, 255, 50));
					g2.fillRect(padding + (int) w, y - 4,
							(int) (((double) poll.statistics.get(i).get("away")) / max * w),
							h * 2);
					g2.setColor(colorText);
					String s = poll.statistics.get(i).get("label").toString();
					if (labels.containsKey(s))
						s = labels.get(s);
					g2.drawString(s, padding + (int) w - g2.getFontMetrics().stringWidth(s) / 2, y + h);
					s = "" + (int) (((double) poll.statistics.get(i).get("home")) + 0.5);
					g2.drawString(s, padding + (int) w * 0.4f - g2.getFontMetrics().stringWidth(s), y + h);
					s = "" + (int) (((double) poll.statistics.get(i).get("away")) + 0.5);
					g2.drawString(s, padding + (int) w * 1.6f, y + h);
					y += delta * padding;
				}
			}
		}

		private void result(final Graphics2D g2, final Font customFont, PollMatchDay poll, PollResult result,
				final Color colorText) throws Exception {
			g2.setFont(customFont.deriveFont(16f));
			final int h = g2.getFontMetrics().getHeight();
			final int total = result.participants;
			int y = padding;
			final boolean prediction = "Prediction".equals(poll.type);
			final String text = result.answers.get("q0").containsKey("t") ? result.answers.get("q0").get("t").toString()
					: null;
			final List<String> x = new ArrayList<>();
			final String leftPad = "000000000000000";
			@SuppressWarnings("unchecked")
			final List<Integer> answers = (List<Integer>) result.answers.get("q0").get("a");
			for (int i = 0; i < answers.size() - (prediction ? 1 : 0); i++)
				x.add(leftPad.substring(answers.get(i).toString().length()) + answers.get(i) + "_"
						+ poll.questions.get(0).answers.get(i).answer);
			if (prediction) {
				if (text != null) {
					final Pattern predictionText = Pattern.compile("(\\d+)[ ]*[ :-]+[ ]*(\\d+)", Pattern.MULTILINE);
					final String[] answersText = text.substring(5, text.length() - 6).split("</div><div>");
					final Map<String, Integer> results = new HashMap<>();
					for (final String s : answersText) {
						final Matcher m = predictionText.matcher(s);
						if (m.find()) {
							final String key = m.group(1) + " : " + m.group(2);
							if (results.containsKey(key))
								results.put(key, results.get(key) + 1);
							else
								results.put(key, 1);
						}
					}
					results.keySet().stream().forEach(key -> {
						x.add(leftPad.substring(("" + results.get(key)).length()) + results.get(key) + "_" + key);
					});
				}
			}
			Collections.sort(x);
			for (int i = x.size() - 1; i >= 0 && i > x.size() - 7; i--) {
				final String[] s = x.get(i).split("_");
				if (s[1].indexOf("<explain") > 0)
					s[1] = s[1].substring(0, s[1].indexOf("<explain")).trim();
				final int percent = (int) (100.0 * Integer.parseInt(s[0]) / total + 0.5);
				if (percent < 1)
					break;
				g2.setColor(new Color(255, 100, 0, 50));
				g2.fillRoundRect(padding, y, width - 2 * padding, h * 2, 10, 10);
				g2.setColor(new Color(255, 100, 0, 120));
				g2.fillRoundRect(padding, y, (width - 2 * padding) * percent / 100, h * 2, 10, 10);
				g2.setColor(colorText);
				g2.drawString(percent + "%", padding * 1.8f, y + h + 5);
				g2.drawString(s[1], padding * 4.5f, y + h + 5);
				y += 2.3 * padding;
			}
		}

		private void draw(final String url, final Graphics2D g, final int x, final int y, final int height,
				final int pos) throws Exception {
			final String label = STORAGE_PREFIX + url.substring(url.lastIndexOf('/', url.length() - 10));
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='" + label + "'");
			final Result result = repository.list(params);
			final String data;
			if (result.size() > 0)
				data = (String) result.get(0).get("storage.storage");
			else {
				final Storage storage = new Storage();
				storage.setLabel(label);
				storage.setStorage(EntityUtil.getImage(url, 0, 0));
				repository.save(storage);
				data = Attachment.resolve(storage.getStorage());
			}
			final BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder()
					.decode(data.split(Attachment.SEPARATOR)[1])));
			final int paddingLogos = -10;
			final int w = image.getWidth() / image.getHeight() * height;
			g.drawImage(image, x - (pos == 1 ? 0 : w) + pos * paddingLogos, y,
					x + (pos == 1 ? w : 0) + pos * paddingLogos,
					height + y, 0, 0, image.getWidth(), image.getHeight(), null);
		}
	}

	int currentSeason() {
		final LocalDateTime now = LocalDateTime.now();
		return now.getYear() - (now.getMonth().getValue() < 6 ? 1 : 0);
	}

	public String retrieveMatchDays(final int pastMatches, final int futureMatches, final List<Integer> teamIds) {
		final Map<String, String> matches = new HashMap<>();
		for (int teamId : teamIds) {
			final JsonNode matchDays = get("team=" + teamId + "&season=" + currentSeason());
			if (matchDays != null) {
				final Map<String, String> matchesFutureList = new HashMap<>();
				final Map<String, String> matchesPastList = new HashMap<>();
				for (int i = 0; i < matchDays.size(); i++) {
					if (!"TBD".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
						final long timestamp = matchDays.get(i).get("fixture").get("timestamp").asLong();
						final Instant startDate = Instant.ofEpochMilli(timestamp * 1000);
						final String homeName = matchDays.get(i).get("teams").get("home").get("name").asText()
								.replace("Munich", "München");
						final String awayName = matchDays.get(i).get("teams").get("away").get("name").asText()
								.replace("Munich", "München");
						final String homeGoals = matchDays.get(i).get("goals").get("home").isNull() ? "&nbsp;" : matchDays.get(i).get("goals").get("home").asText();
						final String awayGoals = matchDays.get(i).get("goals").get("away").isNull() ? "&nbsp;" : matchDays.get(i).get("goals").get("away").asText();
						final String leagueName = matchDays.get(i).get("league").get("name").asText();
						final String venue = matchDays.get(i).get("fixture").get("venue").get("name").asText();
						final String city = matchDays.get(i).findPath("fixture").get("venue").get("city").asText();
						(timestamp > System.currentTimeMillis() ? matchesFutureList : matchesPastList).put(timestamp + "." + teamId,
								"<match><header>" + leagueName + " · " + venue + " · " + city + " · " + formatDate(timestamp, null) + "</header>"
								+ "<home" + (matchDays.get(i).get("teams").get("home").get("id").asInt() == teamId ? " class=\"highlight\"" : "") + ">"
								+ homeName + "</home><goals>" + homeGoals + "</goals><sep>:</sep><goals>"
								+ awayGoals + "</goals><away" + (matchDays.get(i).get("teams").get("away").get("id").asInt() == teamId ? " class=\"highlight\"" : "") + ">" + awayName + "</away></match>");
					}
				}
				List<String> sortedKeys = new ArrayList(matchesPastList.keySet());
				Collections.sort(sortedKeys);
				for (int i = sortedKeys.size() - 1; sortedKeys.size() - i < pastMatches && i >= 0; i--)
					matches.put(sortedKeys.get(i), matchesPastList.get(sortedKeys.get(i)));
				sortedKeys = new ArrayList(matchesFutureList.keySet());
				Collections.sort(sortedKeys);
				for (int i = 0; i < futureMatches && i < sortedKeys.size(); i++)
					matches.put(sortedKeys.get(i), matchesFutureList.get(sortedKeys.get(i)));
			}
		}
		final List<String> sortedKeys = new ArrayList(matches.keySet());
		Collections.sort(sortedKeys);
		final StringBuilder s = new StringBuilder();
		for (String key : sortedKeys)
			s.insert(0, matches.get(key));
		s.insert(0, "<style>header{font-size:0.7em;}match{padding-top:1em;display:inline-block;width:100%;}home,away{width:42%;display:inline-block;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;}home{text-align:right;}away{text-align:left;}goals{width:8%;display:inline-block;text-align:center;overflow:hidden;}sep{position:absolute;margin-left:-0.1em;}.highlight{font-weight:bold;}</style><matchDays style=\"text-align:center;display:inline-block;\">");
		s.append("</matchDays>");
		return s.toString();
	}

	public CronResult run() {
		final CronResult result = new CronResult();
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			final BigInteger clientId = (BigInteger) list.get(i).get("client.id");
			final JsonNode json = Json.toNode(list.get(i).get("client.storage").toString());
			if (json.has("matchDays")) {
				for (int i2 = 0; i2 < json.get("matchDays").size(); i2++) {
					try {
						final int teamId = json.get("matchDays").get(i2).asInt();
						BigInteger id = synchronize.prediction(clientId, teamId);
						if (id != null)
							result.body += "\nprediction: " + id;
						id = synchronize.playerOfTheMatch(clientId, teamId);
						if (id != null)
							result.body += "\npoll: " + id;
						final String s = synchronize.result(clientId);
						if (s.length() > 0)
							result.body += "\nresultAndNotify: " + s;
						result.body += synchronize.updateMatchdays(clientId);
					} catch (final Exception e) {
						if (result.exception == null)
							result.exception = e;
					}
				}
			}
		}
		return result;
	}

	private String formatDate(final long seconds, final String format) {
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds),
				TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId())
				.format(DateTimeFormatter.ofPattern(format == null ? "d.M.yyyy H:mm" : format));
	}

	public List<FutureEvent> futureEvents(final int teamId) {
		final List<FutureEvent> events = new ArrayList<>();
		final JsonNode json = get("team=" + teamId + "&season=" + currentSeason());
		for (int i = 0; i < json.size(); i++) {
			if ("NS".equals(json.get(i).get("fixture").get("status").get("short").asText()))
				events.add(new FutureEvent(json.get(i).get("fixture").get("timestamp").asLong() * 1000,
						json.get(i).get("teams").get("home").get("name").asText() + " : "
								+ json.get(i).get("teams").get("away").get("name").asText()));
		}
		return events;
	}

	protected synchronized JsonNode get(final String url) {
		JsonNode fixture = null;
		final String label = STORAGE_PREFIX + url;
		final QueryParams params = new QueryParams(Query.misc_listStorage);
		params.setSearch("storage.label='" + label + "'");
		final Result result = repository.list(params);
		if (result.size() == 0 || (fixture = needUpdate(result.get(0), url)) == null) {
			if (System.currentTimeMillis() - pauseUntil < 0) {
				String unit;
				double i = (pauseUntil - System.currentTimeMillis()) / 1000;
				unit = " seconds";
				if (i > 120) {
					i /= 60;
					unit = " minutes";
				}
				if (i > 120) {
					i /= 60;
					unit = " hours";
				}
				throw new RuntimeException(
						"Too many requests!\nURL: " + url + "\nRemaining pause " + ((int) (i + 0.5)) + unit);
			}
			fixture = WebClient
					.create("https://v3.football.api-sports.io/fixtures?" + url)
					.get()
					.header("x-rapidapi-key", token)
					.header("x-rapidapi-host", "v3.football.api-sports.io")
					.retrieve()
					.toEntity(JsonNode.class).block().getBody();
			if (fixture != null && fixture.has("response")) {
				pauseUntil = fixture.get("errors").has("rateLimit")
						? Instant.now().plus(Duration.ofMinutes(11)).toEpochMilli()
						: fixture.get("errors").has("requests")
								? Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS).toEpochMilli()
								: 0;
				if (pauseUntil == 0) {
					final Storage storage = result.size() == 0 ? new Storage()
							: repository.one(Storage.class, (BigInteger) result.get(0).get("storage.id"));
					storage.setLabel(label);
					storage.setStorage(Json.toString(fixture));
					repository.save(storage);
				}
			} else
				notificationService.createTicket(TicketType.ERROR, "FIXTURE not FOUND", url, null);
		}
		if (fixture == null)
			throw new RuntimeException(url + " not found");
		return fixture.get("response");
	}

	JsonNode needUpdate(final Map<String, Object> storage, final String url) {
		JsonNode fixture = null;
		if (!Strings.isEmpty(storage.get("storage.storage")))
			fixture = Json.toNode(storage.get("storage.storage").toString());
		if (fixture == null || fixture.has("errors")
				&& (fixture.get("errors").has("rateLimit") || fixture.get("errors").has("requests")))
			return null;
		if (url.contains("season=" + currentSeason()) && url.contains("team=") &&
				Instant.ofEpochMilli(((Timestamp) storage.get("storage.createdAt")).getTime())
						.plus(Duration.ofDays(1)).isBefore(Instant.now())
				&& (storage.get("storage.modifiedAt") == null
						|| Instant.ofEpochMilli(((Timestamp) storage.get("storage.modifiedAt")).getTime())
								.plus(Duration.ofDays(1)).isBefore(Instant.now()))) {
			final JsonNode responses = fixture.get("response");
			for (int i = 0; i < responses.size(); i++) {
				final JsonNode f = responses.get(i).has("fixture") ? responses.get(i).get("fixture") : null;
				if (f != null && f.has("timestamp") && "TBD".equals(f.get("status").get("short").asText())) {
					final Instant time = Instant.ofEpochSecond(f.get("timestamp").asLong());
					if (time.isAfter(Instant.now()) && time.minus(Duration.ofDays(14)).isBefore(Instant.now()))
						return null;
					break;
				}
			}
		}
		return fixture;
	}
}
