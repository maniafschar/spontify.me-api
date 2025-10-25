package com.jq.findapp.service;

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
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.Event;
import com.jq.findapp.entity.Event.FutureEvent;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.model.Fixture;
import com.jq.findapp.service.model.Match;
import com.jq.findapp.service.model.Players;
import com.jq.findapp.service.model.PlayersTeam;
import com.jq.findapp.service.model.Response;
import com.jq.findapp.service.model.Statistic;
import com.jq.findapp.util.Entity;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
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

	@Autowired
	private Text text;

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
			final List<Match> matches = MatchDayService.this
					.get("team=" + teamId + "&season=" + MatchDayService.this.currentSeason());
			for (int i = 0; i < matches.size(); i++) {
				if ("NS".equals(matches.get(i).fixture.status.myshort)) {
					final Instant date = Instant.ofEpochSecond(matches.get(i).fixture.timestamp);
					if (date.getEpochSecond() >= now && date.minus(Duration.ofDays(1)).getEpochSecond() < now) {
						final PollMatchDay poll = new PollMatchDay();
						poll.type = "Prediction";
						poll.home = matches.get(i).teams.home.logo;
						poll.away = matches.get(i).teams.away.logo;
						poll.homeName = matches.get(i).teams.home.name
								.replace("Munich", "München");
						poll.awayName = matches.get(i).teams.away.name
								.replace("Munich", "München");
						poll.homeId = matches.get(i).teams.home.id;
						poll.awayId = matches.get(i).teams.away.id;
						poll.league = matches.get(i).league.logo;
						poll.leagueName = matches.get(i).league.name;
						poll.timestamp = matches.get(i).fixture.timestamp;
						poll.venue = matches.get(i).fixture.venue.name;
						poll.city = matches.get(i).fixture.venue.city;
						poll.location = matches.get(i).teams.home.id == teamId ? "home"
								: "away";
						poll.subject = poll.homeName + " : " + poll.awayName + " (" + poll.city + " · " + poll.venue
								+ " · " + MatchDayService.this.formatDate(poll.timestamp, null) + ")";
						poll.textId = TextId.notification_clientMarketingPollPrediction;
						final Instant end = Instant.ofEpochSecond(poll.timestamp)
								.minus(Duration.ofMinutes(15));
						final QueryParams params = new QueryParams(Query.misc_listMarketing);
						params.setSearch(
								"clientMarketing.endDate=cast('" + end + "' as timestamp) and clientMarketing.clientId="
										+ clientId);
						if (MatchDayService.this.repository.list(params).size() == 0) {
							this.predictionAddStatistics(clientId, poll);
							final ClientMarketing clientMarketing = new ClientMarketing();
							clientMarketing.setCreateResult(true);
							clientMarketing.setClientId(clientId);
							clientMarketing.setSkills("9." + teamId);
							clientMarketing.setStartDate(new Timestamp(end
									.minus(Duration.ofDays(1)).toEpochMilli()));
							clientMarketing.setEndDate(new Timestamp(end.toEpochMilli()));
							clientMarketing.setStorage(Json.toString(poll));
							clientMarketing.setImage(Attachment.createImage(".png",
									MatchDayService.this.image.create(poll, "Ergebnistipps",
											MatchDayService.this.repository.one(Client.class, clientId), null)));
							MatchDayService.this.repository.save(clientMarketing);
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
			final List<Match> result = new ArrayList<>();
			for (int i = FIRST_YEAR; i <= MatchDayService.this.currentSeason(); i++) {
				final List<Match> matches = MatchDayService.this.get("team=" + teamId + "&season=" + i);
				for (int i2 = 0; i2 < matches.size(); i2++) {
					if (matches.get(i2).teams.home.id == poll.homeId
							&& matches.get(i2).teams.away.id == poll.awayId
							&& "FT".equals(matches.get(i2).fixture.status.myshort)) {
						final Match fixture = MatchDayService.this
								.get("id=" + matches.get(i2).fixture.id).get(0);
						if (fixture != null && fixture.statistics != null && fixture.statistics.size() > 1
								&& fixture.statistics.get(0).statistics != null) {
							result.add(fixture);
							if (result.size() > 7)
								break;
						}
					}
				}
			}

			final Map<String, Double> homeList = new HashMap<>();
			final Map<String, Double> awayList = new HashMap<>();
			final List<String> labels = new ArrayList<>();
			String added = "|";
			for (int i = 0; i < result.size(); i++) {
				for (int i2 = 0; i2 < result.get(i).statistics.get(0).statistics.size(); i2++) {
					final String label = result.get(i).statistics.get(0).statistics.get(i2).type;
					if (!homeList.containsKey(label)) {
						homeList.put(label, 0.0);
						awayList.put(label, 0.0);
						labels.add(label);
					}
					if (result.get(i).statistics.get(0).statistics.get(i2).value == null)
						MatchDayService.this.notificationService.createTicket(TicketType.ERROR, "MDS",
								Json.toPrettyString(result.get(i).statistics.get(0).statistics.get(i2)), null);
					if (result.get(i).statistics.get(1).statistics.get(i2).value == null)
						MatchDayService.this.notificationService.createTicket(TicketType.ERROR, "MDS",
								Json.toPrettyString(result.get(i).statistics.get(1).statistics.get(i2)), null);
					homeList.put(label,
							homeList.get(label)
									+ Double.valueOf(
											result.get(i).statistics.get(0).statistics.get(i2).value.replace("%", "")));
					awayList.put(label,
							awayList.get(label)
									+ Double.valueOf(
											result.get(i).statistics.get(1).statistics.get(i2).value.replace("%", "")));
				}
				final String s = result.get(i).goals.home + " : " + result.get(i).goals.away;
				if (!added.contains("|" + s + "|")) {
					final Answer answer = new Answer();
					answer.answer = s;
					poll.questions.get(0).answers.add(answer);
					added += s + "|";
				}
				if (i < 8)
					poll.matches.add(s + "|"
							+ MatchDayService.this.formatDate(result.get(i).fixture.timestamp, null));
			}
			final List<Map<String, Object>> statistics = new ArrayList<>();
			for (int i = 0; i < labels.size(); i++) {
				final Map<String, Object> row = new HashMap<>();
				row.put("home", homeList.get(labels.get(i)) / result.size());
				row.put("away", awayList.get(labels.get(i)) / result.size());
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
			poll.prolog = "<b>Ergebnistipps</b> zum " + poll.leagueName
					+ " Spiel<div style=\"padding:1em 0;font-weight:bold;\">" + poll.homeName + " - " + poll.awayName
					+ "</div>vom <b>" + MatchDayService.this.formatDate(poll.timestamp, null)
					+ "</b>. Möchtest Du teilnehmen?";
			poll.epilog = "Lieben Dank für Deine Teilnahme!\nDas Ergebnis wird kurz vor dem Spiel hier bekanntgegeben.";
		}

		BigInteger playerOfTheMatch(final BigInteger clientId, final int teamId) throws Exception {
			final List<Match> matchDays = MatchDayService.this
					.get("team=" + teamId + "&season=" + MatchDayService.this.currentSeason());
			if (matchDays != null) {
				for (int i = 0; i < matchDays.size(); i++) {
					if ("FT".equals(matchDays.get(i).fixture.status.myshort)) {
						final Instant startDate = Instant.ofEpochSecond(matchDays.get(i).fixture.timestamp)
								.plus(Duration.ofHours(8));
						if (startDate.isBefore(Instant.now())
								&& startDate.plus(Duration.ofDays(4)).isAfter(Instant.now())) {
							final QueryParams params = new QueryParams(Query.misc_listMarketing);
							params.setSearch(
									"clientMarketing.startDate>=cast('" + startDate
											+ "' as timestamp) and clientMarketing.clientId=" + clientId
											+ " and clientMarketing.skills='9." + teamId + "'");
							if (MatchDayService.this.repository.list(params).size() > 0)
								break;
							final Match matchDay = MatchDayService.this.get("id=" + matchDays.get(i).fixture.id).get(0);
							if (matchDay != null && this.anyPlayerWith90Minutes(matchDay.players)) {
								final List<PlayersTeam> playersTeam = matchDay.players;
								final List<Players> players = playersTeam
										.get(playersTeam.get(0).team.id == teamId ? 0 : 1).players;
								final PollMatchDay poll = new PollMatchDay();
								poll.type = "PlayerOfTheMatch";
								poll.home = matchDay.teams.home.logo;
								poll.away = matchDay.teams.away.logo;
								poll.homeName = matchDay.teams.home.name.replace("Munich", "München");
								poll.awayName = matchDay.teams.away.name.replace("Munich", "München");
								poll.homeId = matchDay.teams.home.id;
								poll.awayId = matchDay.teams.away.id;
								poll.league = matchDay.league.logo;
								poll.timestamp = matchDay.fixture.timestamp;
								poll.venue = matchDay.fixture.venue.name;
								poll.city = matchDay.fixture.venue.city;
								poll.textId = TextId.notification_clientMarketingPollPlayerOfTheMath;
								poll.location = matchDay.teams.home.id == teamId ? "home" : "away";
								poll.prolog = "Umfrage <b>Spieler des Spiels</b> zum "
										+ matchDay.league.name +
										" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
										+ poll.homeName + " - " + poll.awayName
										+ "</div>vom <b>"
										+ MatchDayService.this.formatDate(matchDay.fixture.timestamp, null)
										+ "</b>. Möchtest Du teilnehmen?";
								poll.subject = poll.homeName + " : " + poll.awayName + " (" + poll.city + " · "
										+ poll.venue + " · " + MatchDayService.this.formatDate(poll.timestamp, null)
										+ ")";
								final Question question = new Question();
								question.question = "Wer war für Dich Spieler des Spiels?";
								this.playerOfTheMatchAddAnswers(question.answers, players);
								poll.questions.add(question);
								final ClientMarketing clientMarketing = new ClientMarketing();
								clientMarketing.setSkills("9." + teamId);
								clientMarketing.setCreateResult(true);
								clientMarketing.setStartDate(new Timestamp(System.currentTimeMillis()));
								clientMarketing
										.setEndDate(
												new Timestamp(Instant.now().plus(Duration.ofDays(1)).toEpochMilli()));
								final String endDate = MatchDayService.this.formatDate(
										clientMarketing.getEndDate().getTime() / 1000 + 10 * 60, null);
								clientMarketing.setClientId(clientId);
								clientMarketing.setImage(Attachment.createImage(".png",
										MatchDayService.this.image.create(poll, "Spieler",
												MatchDayService.this.repository.one(Client.class, clientId), null)));
								poll.epilog = "Lieben Dank für Deine Teilnahme!\nDas Ergebnis wird am " + endDate
										+ " hier bekanntgegeben.";
								clientMarketing.setStorage(Json.toString(poll));
								MatchDayService.this.repository.save(clientMarketing);
								return clientMarketing.getId();
							}
						}
					}
				}
			}
			return null;
		}

		private boolean anyPlayerWith90Minutes(final List<PlayersTeam> players) {
			if (players == null)
				return false;
			for (int i = 0; i < players.size(); i++) {
				for (int i2 = 0; i2 < players.get(i).players.size(); i2++) {
					final Statistic statistics = players.get(i).players.get(i2).statistics.get(0);
					if (statistics.games.minutes >= 90)
						return true;
				}
			}
			return false;
		}

		private void playerOfTheMatchAddAnswers(final List<Answer> answers, final List<Players> players) {
			for (int i = 0; i < players.size(); i++) {
				final Statistic statistics = players.get(i).statistics.get(0);
				if (statistics.games.minutes > 0) {
					String s = players.get(i).player.name +
							"<explain>" + statistics.games.minutes +
							(statistics.games.minutes > 1 ? " gespielte Minuten"
									: " gespielte Minute");
					if (statistics.goals.total > 0)
						s += this.getLine(statistics.goals.total, " Tor", " Tore");
					if (statistics.shots.total > 0)
						s += this.getLine(statistics.shots.total, " Torschuss, ",
								" Torschüsse, ")
								+ statistics.shots.on
								+ " aufs Tor";
					if (statistics.goals.assists > 0)
						s += this.getLine(statistics.goals.assists, " Assist", " Assists");
					if (statistics.passes.total > 0)
						s += this.getLine(statistics.passes.total, " Pass, ", " Pässe, ")
								+ statistics.passes.accuracy + " angekommen";
					if (statistics.duels.total > 0)
						s += this.getLine(statistics.duels.total, " Duell, ", " Duelle, ")
								+ statistics.duels.won + " gewonnen";
					if (statistics.cards.yellow > 0 && statistics.cards.red > 0)
						s += "<br/>Gelberote Karte erhalten";
					else if (statistics.cards.yellow > 0)
						s += "<br/>Gelbe Karte erhalten";
					if (statistics.cards.red > 0)
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
			final Result list = MatchDayService.this.repository.list(params);
			String result = "";
			for (int i = 0; i < list.size(); i++) {
				final ClientMarketingResult clientMarketingResult = MatchDayService.this.marketingService
						.synchronizeResult(
								(BigInteger) list.get(i).get("clientMarketing.id"));
				final PollMatchDay poll = Json.toObject(Attachment.resolve(
						MatchDayService.this.repository
								.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId())
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
							MatchDayService.this.image.create(poll, prefix,
									MatchDayService.this.repository.one(Client.class, clientId),
									clientMarketingResult)));
					MatchDayService.this.repository.save(clientMarketingResult);
					if ("PlayerOfTheMatch".equals(poll.type))
						prefix = "Umfrage Spieler des Spiels";
					result += clientMarketingResult.getId() + " ";
				} else
					MatchDayService.this.repository.save(clientMarketingResult);
			}
			return result.trim();
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
			final BufferedImage output = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
			final Color colorText = new Color(Integer.valueOf(color3[0].trim()), Integer.valueOf(color3[1].trim()),
					Integer.valueOf(color3[2].trim()));
			final Graphics2D g2 = output.createGraphics();
			g2.setComposite(AlphaComposite.Src);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final RadialGradientPaint gradient = new RadialGradientPaint(this.width / 2 - 2 * this.padding,
					this.height / 2 - 2 * this.padding,
					this.height,
					new float[] { .3f, 1f },
					new Color[] {
							new Color(Integer.valueOf(color1[0].trim()), Integer.valueOf(color1[1].trim()),
									Integer.valueOf(color1[2].trim())),
							new Color(Integer.valueOf(color2[0].trim()), Integer.valueOf(color2[1].trim()),
									Integer.valueOf(color2[2].trim()))
					});
			g2.setPaint(gradient);
			g2.fill(new Rectangle2D.Float(0, 0, this.width, this.height));
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
					clientMarketingResult == null && "PlayerOfTheMatch".equals(poll.type) ? 1 : 0.15f));
			final int h = (int) (this.height * 0.4);
			if (!homeMatch)
				this.draw(urlHome, g2, this.width / 2, this.padding, h, -1);
			this.draw(urlAway, g2, this.width / 2, this.padding, h, 1);
			if (homeMatch)
				this.draw(urlHome, g2, this.width / 2, this.padding, h, -1);
			this.draw(urlLeague, g2, this.width - this.padding, this.padding, this.height / 4, 0);
			try (final InputStream in = this.getClass().getResourceAsStream("/Comfortaa-Regular.ttf")) {
				final Font customFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(50f);
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
				g2.setFont(customFont);
				g2.setColor(colorText);
				String s = "Umfrage";
				if (clientMarketingResult != null)
					s += "ergebnis";
				g2.drawString(s, (this.width - g2.getFontMetrics().stringWidth(s)) / 2, this.height / 20 * 14.5f);
				g2.setFont(customFont.deriveFont(20f));
				s = subtitlePrefix + " des Spiels vom " + MatchDayService.this.formatDate(poll.timestamp, null);
				g2.drawString(s, (this.width - g2.getFontMetrics().stringWidth(s)) / 2, this.height / 20 * 17.5f);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));
				g2.setFont(customFont.deriveFont(12f));
				s = "© " + LocalDateTime.now().getYear() + " " + client.getName();
				g2.drawString(s, this.width - g2.getFontMetrics().stringWidth(s) - this.padding,
						this.height - this.padding);
				if (clientMarketingResult != null)
					this.result(g2, customFont, poll,
							Json.toObject(Attachment.resolve(clientMarketingResult.getStorage()), PollResult.class),
							colorText);
				else if ("Prediction".equals(poll.type))
					this.prediction(g2, customFont, poll, colorText, client.getId());
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
		}

		private void prediction(final Graphics2D g2, final Font customFont, final PollMatchDay poll,
				final Color colorText, final BigInteger clientId) throws Exception {
			g2.setFont(customFont.deriveFont(12f));
			final int h = g2.getFontMetrics().getHeight();
			int y = this.padding;
			final double w = this.width * 0.3, delta = 1.6;
			for (int i = 0; i < poll.matches.size(); i++) {
				final String[] s = poll.matches.get(poll.matches.size() - i - 1).split("\\|");
				g2.drawString(s[0], this.width - this.padding - 150 - g2.getFontMetrics().stringWidth(s[0]) / 2, y + h);
				g2.drawString(s[1], this.width - this.padding - g2.getFontMetrics().stringWidth(s[1]), y + h);
				y += delta * this.padding;
			}
			y = this.padding;
			if (poll.statistics.size() == 0) {
				final String[] s = new String[4];
				s[0] = "Das erste Spiel seit langem zwischen";
				s[1] = poll.homeName + " : " + poll.awayName;
				s[2] = "in " + poll.city + " · " + poll.venue;
				s[3] = "Was meinst Du, wie geht das Spiel aus?";
				g2.setFont(customFont.deriveFont(24f));
				final double pad = g2.getFontMetrics().getHeight() * 1.8;
				y += pad;
				g2.drawString(s[0], (this.width - g2.getFontMetrics().stringWidth(s[0])) / 2, y + h);
				y += pad;
				g2.drawString(s[1], (this.width - g2.getFontMetrics().stringWidth(s[1])) / 2, y + h);
				y += pad;
				g2.drawString(s[2], (this.width - g2.getFontMetrics().stringWidth(s[2])) / 2, y + h);
				y += pad;
				g2.drawString(s[3], (this.width - g2.getFontMetrics().stringWidth(s[3])) / 2, y + h);
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
					g2.fillRect(this.padding + (int) (w - w2), y - 4, (int) w2, h * 2);
					g2.setColor(new Color(0, 100, 255, 50));
					g2.fillRect(this.padding + (int) w, y - 4,
							(int) (((double) poll.statistics.get(i).get("away")) / max * w),
							h * 2);
					g2.setColor(colorText);
					String s = poll.statistics.get(i).get("label").toString();
					if (labels.containsKey(s))
						s = labels.get(s);
					g2.drawString(s, this.padding + (int) w - g2.getFontMetrics().stringWidth(s) / 2, y + h);
					s = "" + (int) (((double) poll.statistics.get(i).get("home")) + 0.5);
					g2.drawString(s, this.padding + (int) w * 0.4f - g2.getFontMetrics().stringWidth(s), y + h);
					s = "" + (int) (((double) poll.statistics.get(i).get("away")) + 0.5);
					g2.drawString(s, this.padding + (int) w * 1.6f, y + h);
					y += delta * this.padding;
				}
			}
		}

		private void result(final Graphics2D g2, final Font customFont, final PollMatchDay poll,
				final PollResult result,
				final Color colorText) throws Exception {
			g2.setFont(customFont.deriveFont(16f));
			final int h = g2.getFontMetrics().getHeight();
			final int total = result.participants;
			int y = this.padding;
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
				g2.fillRoundRect(this.padding, y, this.width - 2 * this.padding, h * 2, 10, 10);
				g2.setColor(new Color(255, 100, 0, 120));
				g2.fillRoundRect(this.padding, y, (this.width - 2 * this.padding) * percent / 100, h * 2, 10, 10);
				g2.setColor(colorText);
				g2.drawString(percent + "%", this.padding * 1.8f, y + h + 5);
				g2.drawString(s[1], this.padding * 4.5f, y + h + 5);
				y += 2.3 * this.padding;
			}
		}

		private void draw(final String url, final Graphics2D g, final int x, final int y, final int height,
				final int pos) throws Exception {
			final String label = STORAGE_PREFIX + url.substring(url.lastIndexOf('/', url.length() - 10));
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='" + label + "'");
			final Result result = MatchDayService.this.repository.list(params);
			final String data;
			if (result.size() > 0)
				data = (String) result.get(0).get("storage.storage");
			else {
				final Storage storage = new Storage();
				storage.setLabel(label);
				storage.setStorage(Entity.getImage(url, 0, 0));
				MatchDayService.this.repository.save(storage);
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

	public String retrieveMatchDays(final int pastMatches, final int futureMatches, final Contact contact) {
		final List<Integer> teamIds = Arrays.asList(contact.getSkills().split("\\|"))
				.stream().filter(e -> e.startsWith("9.")).map(e -> Integer.valueOf(e.substring(2)))
				.collect(Collectors.toList());
		final Map<String, String> matches = new HashMap<>();
		for (final int teamId : teamIds) {
			try {
				final List<Match> matchDays = this.get("team=" + teamId + "&season=" + this.currentSeason());
				if (matchDays != null) {
					final Map<String, String> matchesFutureList = new HashMap<>();
					final Map<String, String> matchesPastList = new HashMap<>();
					for (int i = 0; i < matchDays.size(); i++) {
						if (!"TBD".equals(matchDays.get(i).fixture.status.myshort)) {
							final long timestamp = matchDays.get(i).fixture.timestamp;
							final String homeName = matchDays.get(i).teams.home.name.replace("Munich", "München");
							final String awayName = matchDays.get(i).teams.away.name.replace("Munich", "München");
							final String homeGoals = "" + matchDays.get(i).goals.home;
							final String awayGoals = "" + matchDays.get(i).goals.away;
							final String leagueName = matchDays.get(i).league.name;
							final String venue = matchDays.get(i).fixture.venue.name;
							final String city = matchDays.get(i).fixture.venue.city;
							("NS".equals(matchDays.get(i).fixture.status.myshort)
									? matchesFutureList
									: matchesPastList).put(timestamp + "." + teamId,
											"<match skills=\"9." + teamId + "\"><header>" + leagueName + " · " + venue
													+ " · " + city + " · "
													+ this.formatDate(timestamp, contact) + "</header>"
													+ "<home"
													+ (matchDays.get(i).teams.home.id == teamId ? " class=\"highlight\""
															: "")
													+ ">"
													+ homeName + "</home><goals>" + homeGoals
													+ "</goals><sep>:</sep><goals>"
													+ awayGoals + "</goals><away"
													+ (matchDays.get(i).teams.away.id == teamId ? " class=\"highlight\""
															: "")
													+ ">" + awayName + "</away></match>");
						}
					}
					List<String> sortedKeys = new ArrayList<>(matchesPastList.keySet());
					Collections.sort(sortedKeys);
					for (int i = sortedKeys.size() - 1; sortedKeys.size() - i <= pastMatches && i >= 0; i--)
						matches.put(sortedKeys.get(i), matchesPastList.get(sortedKeys.get(i)));
					sortedKeys = new ArrayList<>(matchesFutureList.keySet());
					Collections.sort(sortedKeys);
					for (int i = 0; i < futureMatches && i < sortedKeys.size(); i++)
						matches.put(sortedKeys.get(i), matchesFutureList.get(sortedKeys.get(i)));
				}
			} catch (final RuntimeException ex) {
				// matchDays not up to date, ignore
			}
		}
		final List<String> sortedKeys = new ArrayList<>(matches.keySet());
		Collections.sort(sortedKeys);
		final StringBuilder s = new StringBuilder();
		for (final String key : sortedKeys)
			s.insert(0, matches.get(key));
		s.insert(0,
				"<style>header{font-size:0.7em;padding-top:1em;}match{display:inline-block;float:left;width:100%;font-size:0.7em;}home,away{width:42%;display:inline-block;white-space:nowrap;text-overflow:ellipsis;overflow:hidden;}home{text-align:right;}away{text-align:left;}goals{width:8%;display:inline-block;text-align:center;overflow:hidden;}sep{position:absolute;margin-left:-0.1em;}.highlight{font-weight:bold;}matchdays{text-align:center;display:inline-block;float:left;}</style><matchDays>");
		s.append("</matchDays>");
		return s.toString();
	}

	@Cron
	public CronResult cron() {
		final CronResult result = new CronResult();
		final Result list = this.repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			final BigInteger clientId = (BigInteger) list.get(i).get("client.id");
			final JsonNode json = Json.toNode(list.get(i).get("client.storage").toString());
			if (json.has("matchDays")) {
				for (int i2 = 0; i2 < json.get("matchDays").size(); i2++) {
					try {
						final int teamId = json.get("matchDays").get(i2).asInt();
						BigInteger id = this.synchronize.prediction(clientId, teamId);
						if (id != null)
							result.body += "\nprediction: " + id;
						id = this.synchronize.playerOfTheMatch(clientId, teamId);
						if (id != null)
							result.body += "\nplayerOfTheMatch: " + id;
						final String s = this.synchronize.result(clientId);
						if (s.length() > 0)
							result.body += "\nresult: " + s;
					} catch (final Exception e) {
						if (result.exception == null)
							result.exception = e;
					}
				}
			}
		}
		return result;
	}

	private String formatDate(final long seconds, Contact contact) {
		final LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds),
				TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId());
		if (contact == null) {
			contact = new Contact();
			contact.setLanguage("DE");
		}
		return this.text.getText(contact, TextId.valueOf("date_weekday" + (time.getDayOfWeek().getValue() % 7)))
				+ " " + time.format(DateTimeFormatter.ofPattern("d.M.yyyy H:mm"));
	}

	public List<FutureEvent> futureEvents(final int teamId) {
		final List<FutureEvent> events = new ArrayList<>();
		final List<Match> matches = this.get("team=" + teamId + "&season=" + this.currentSeason());
		for (int i = 0; i < matches.size(); i++) {
			if ("NS".equals(matches.get(i).fixture.status.myshort))
				events.add(new FutureEvent(matches.get(i).fixture.timestamp * 1000,
						matches.get(i).teams.home.name + " : " + matches.get(i).teams.away.name));
		}
		return events;
	}

	public Event retrieveNextMatchDay(final int teamId) {
		return null;
	}

	protected synchronized List<Match> get(final String url) {
		Response response = null;
		final String label = STORAGE_PREFIX + url;
		final QueryParams params = new QueryParams(Query.misc_listStorage);
		params.setSearch("storage.label='" + label + "'");
		final Result result = this.repository.list(params);
		if (result.size() == 0 || (response = this.needUpdate(result.get(0), url)) == null) {
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
			// need to be done this way to avoid "errors: []" exception
			final String s = WebClient
					.create("https://v3.football.api-sports.io/fixtures?" + url)
					.get()
					.header("x-rapidapi-key", this.token)
					.header("x-rapidapi-host", "v3.football.api-sports.io")
					.retrieve()
					.toEntity(String.class).block().getBody();
			try {
				response = Json.toObject(s, Response.class);
				if (response.response != null) {
					pauseUntil = response.errors == null ? 0
							: response.errors.rateLimit != null
									? Instant.now().plus(Duration.ofMinutes(11)).toEpochMilli()
									: response.errors.requests != null
											? Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS)
													.toEpochMilli()
											: 0;
					if (pauseUntil == 0) {
						final Storage storage = result.size() == 0 ? new Storage()
								: this.repository.one(Storage.class, (BigInteger) result.get(0).get("storage.id"));
						storage.setLabel(label);
						storage.setStorage(Json.toString(response));
						this.repository.save(storage);
					}
				}
			} catch (final Exception ex) {
				this.notificationService.createTicket(TicketType.ERROR, "FIXTURE",
						url + "\n" + s + "\n" + Strings.stackTraceToString(ex), null);
			}
		}
		if (response == null)
			throw new RuntimeException(url + " not found");
		return response.response;
	}

	Response needUpdate(final Map<String, Object> storage, final String url) {
		Response response = null;
		if (!Strings.isEmpty(storage.get("storage.storage")))
			response = Json.toObject(storage.get("storage.storage").toString(), Response.class);
		if (response == null || response.errors != null
				&& (response.errors.rateLimit != null || response.errors.requests != null))
			return null;
		if (url.contains("season=" + this.currentSeason()) && url.contains("team=") &&
				Instant.ofEpochMilli(((Timestamp) storage.get("storage.createdAt")).getTime())
						.plus(Duration.ofDays(1)).isBefore(Instant.now())
				&& (storage.get("storage.modifiedAt") == null
						|| Instant.ofEpochMilli(((Timestamp) storage.get("storage.modifiedAt")).getTime())
								.plus(Duration.ofDays(1)).isBefore(Instant.now()))) {
			final List<Match> responses = response.response;
			for (int i = 0; i < responses.size(); i++) {
				final Fixture f = responses.get(i).fixture;
				if (f != null && f.timestamp > 0 && ("TBD".equals(f.status.myshort)
						|| "NS".equals(f.status.myshort))) {
					final Instant time = Instant.ofEpochSecond(f.timestamp);
					if ("NS".equals(f.status.myshort)) {
						if (time.plus(Duration.ofHours(2)).isBefore(Instant.now())
								&& time.plus(Duration.ofDays(14)).isAfter(Instant.now()))
							return null;
					} else if (time.isAfter(Instant.now()) && time.minus(Duration.ofDays(14)).isBefore(Instant.now()))
						return null;
				}
			}
		}
		return response;
	}
}
