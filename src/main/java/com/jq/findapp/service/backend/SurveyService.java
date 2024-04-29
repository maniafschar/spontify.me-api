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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.EntityUtil;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class SurveyService {
	final Synchonize synchronize = new Synchonize();
	private final Image image = new Image();
	private final Notification notification = new Notification();

	@Autowired
	private Repository repository;

	@Autowired
	private ExternalService externalService;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	@Value("${app.sports.api.token}")
	private String token;

	private static final String STORAGE_PREFIX = "api-sports-";

	class Synchonize {
		private final static int FIRST_YEAR = 2010;

		BigInteger prediction(final BigInteger clientId, final int teamId) throws Exception {
			final long now = Instant.now().getEpochSecond();
			final JsonNode json = get("team=" + teamId + "&season=" + currentSeason());
			final JsonNode clientJson = new ObjectMapper()
					.readTree(repository.one(Client.class, clientId).getStorage());
			for (int i = 0; i < json.size(); i++) {
				if ("NS".equals(json.get(i).get("fixture").get("status").get("short").asText())) {
					final Instant date = Instant
							.ofEpochSecond(json.get(i).get("fixture").get("timestamp").asLong());
					if (date.getEpochSecond() >= now && date.minus(Duration.ofDays(1)).getEpochSecond() < now) {
						final ObjectNode poll = new ObjectMapper().createObjectNode();
						poll.put("type", "Prediction");
						poll.put("home", json.get(i).get("teams").get("home").get("logo").asText());
						poll.put("away", json.get(i).get("teams").get("away").get("logo").asText());
						poll.put("homeName", json.get(i).get("teams").get("home").get("name").asText()
								.replace("Munich", "München"));
						poll.put("awayName", json.get(i).get("teams").get("away").get("name").asText()
								.replace("Munich", "München"));
						poll.put("homeId", json.get(i).get("teams").get("home").get("id").asInt());
						poll.put("awayId", json.get(i).get("teams").get("away").get("id").asInt());
						poll.put("league", json.get(i).get("league").get("logo").asText());
						poll.put("leagueName", json.get(i).get("league").get("name").asText());
						poll.put("timestamp", json.get(i).get("fixture").get("timestamp").asLong());
						poll.put("fixtureId", json.get(i).get("fixture").get("id").asLong());
						poll.put("venue", json.get(i).get("fixture").get("venue").get("name").asText());
						poll.put("city", json.get(i).findPath("fixture").get("venue").get("city").asText());
						poll.put("location",
								json.get(i).get("teams").get("home").get("id").asInt() == teamId ? "home" : "away");
						final Instant end = Instant.ofEpochSecond(poll.get("timestamp").asLong())
								.minus(Duration.ofMinutes(15));
						final QueryParams params = new QueryParams(Query.misc_listMarketing);
						params.setSearch(
								"clientMarketing.endDate=cast('" + end + "' as timestamp) and clientMarketing.clientId="
										+ clientId);
						if (repository.list(params).size() == 0) {
							predictionAddStatistics(clientId, poll);
							final ClientMarketing clientMarketing = new ClientMarketing();
							clientMarketing.setClientId(clientId);
							clientMarketing.setStartDate(new Timestamp(end
									.minus(Duration.ofDays(1)).toEpochMilli()));
							clientMarketing.setEndDate(new Timestamp(end.toEpochMilli()));
							clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
							clientMarketing.setImage(Attachment.createImage(".png",
									image.create(poll, "Ergebnistipps",
											repository.one(Client.class, clientId), null)));
							repository.save(clientMarketing);
							notification.sendPoll(clientMarketing);
							externalService.publishOnFacebook(clientId,
									"Eure Ergebnistipps für das Spiel " + getTeam(clientId, poll)
											+ getOponent(clientId, poll)
											+ "\n\nKlickt auf das Bild, um abzustimmen. Dort wird Eure Stimme erfasst und automatisch in die Wertung übernommen, die Ihr dann kurz vor dem Spiel hier sehen könnt."
											+ (poll.get("statistics").size() > 0
													? "\n\nIm Bild seht ihr die zusammengefassten Statistiken zu den letzen Spielen."
													: "")
											+ (clientJson.has("publishingPostfix")
													? "\n\n" + clientJson.get("publishingPostfix").asText()
													: ""),
									"/rest/marketing/" + clientMarketing.getId());
							return clientMarketing.getId();
						}
					}
				}
			}
			return null;
		}

		private void predictionAddStatistics(final BigInteger clientId, final ObjectNode poll)
				throws Exception {
			final ObjectMapper om = new ObjectMapper();
			final int teamId = poll.get(poll.get("location").asText() + "Id").asInt();
			final ArrayNode matches = om.createArrayNode();
			for (int i = FIRST_YEAR; i <= currentSeason(); i++) {
				final JsonNode json = get("team=" + teamId + "&season=" + i);
				for (int i2 = 0; i2 < json.size(); i2++) {
					if (json.get(i2).get("teams").get("home").get("id").asInt() == poll.get("homeId").asInt()
							&& json.get(i2).get("teams").get("away").get("id").asInt() == poll.get("awayId")
									.asInt()
							&& "FT".equals(json.get(i2).get("fixture").get("status").get("short").asText())) {
						final JsonNode fixture = get("id=" + json.get(i2).get("fixture").get("id").asInt()).get(0);
						if (fixture != null && fixture.has("statistics") && fixture.get("statistics").size() > 1
								&& fixture.get("statistics").get(0).has("statistics")) {
							final ObjectNode match = om.createObjectNode();
							match.put("timestamp", fixture.get("fixture").get("timestamp").asLong());
							match.set("goals", fixture.get("goals"));
							match.set("statistics", fixture.get("statistics"));
							matches.add(match);
							if (matches.size() > 7)
								break;
						}
					}
				}
			}
			final Map<String, Integer> homeList = new HashMap<>();
			final Map<String, Integer> awayList = new HashMap<>();
			final List<String> labels = new ArrayList<>();
			final ArrayNode answers = om.createArrayNode();
			poll.set("matches", om.createArrayNode());
			String added = "|";
			for (int i = 0; i < matches.size(); i++) {
				final JsonNode json = matches.get(i).get("statistics");
				for (int i2 = 0; i2 < json.get(0).get("statistics").size(); i2++) {
					final String label = json.get(0).get("statistics").get(i2).get("type").asText();
					if (!homeList.containsKey(label)) {
						homeList.put(label, 0);
						awayList.put(label, 0);
						labels.add(label);
					}
					homeList.put(label,
							homeList.get(label) + json.get(0).get("statistics").get(i2).get("value").asInt());
					awayList.put(label,
							awayList.get(label) + json.get(1).get("statistics").get(i2).get("value").asInt());
				}
				final String s = matches.get(i).get("goals").get("home").intValue() + " : "
						+ matches.get(i).get("goals").get("away").intValue();
				if (!added.contains("|" + s + "|")) {
					answers.addObject().put("answer", s);
					added += s + "|";
				}
				if (i < 8)
					((ArrayNode) poll.get("matches"))
							.add(s + "|" + formatDate(matches.get(i).get("timestamp").asLong(), "d.M.yyyy H:mm"));
			}
			final ArrayNode statistics = om.createArrayNode();
			for (int i = 0; i < labels.size(); i++) {
				final ObjectNode row = om.createObjectNode();
				row.put("home", homeList.get(labels.get(i)) / matches.size());
				row.put("away", awayList.get(labels.get(i)) / matches.size());
				row.put("label", labels.get(i));
				statistics.add(row);
			}
			String tips = "1 : 1|2 : 1|1 : 0|2 : 0|0 : 0|2 : 2|3 : 1|0 : 1|1 : 2";
			for (int i = answers.size(); i < 8; i++) {
				final String s = tips.substring(0, tips.indexOf('|'));
				tips = tips.substring(tips.indexOf('|') + 1);
				if (!added.contains("|" + s + "|"))
					answers.addObject().put("answer", s);
			}
			poll.set("statistics", statistics);
			answers.addObject().put("answer", "Oder erzielen wir ein anderes Ergebnis?");
			final ArrayNode questions = om.createArrayNode();
			final ObjectNode question = questions.addObject();
			question.put("question", "Erzielen wir eines der letzten Ergebnisse?");
			question.set("answers", answers);
			question.put("textField", true);
			poll.set("questions", questions);
			poll.put("prolog",
					"<b>Ergebnistipps</b> zum "
							+ poll.get("leagueName").asText() +
							" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
							+ poll.get("homeName").asText()
							+ " - "
							+ poll.get("awayName").asText()
							+ "</div>vom <b>"
							+ formatDate(poll.get("timestamp").asLong(), null)
							+ "</b>. Möchtest Du teilnehmen?");
			poll.put("epilog",
					"Lieben Dank für die Teilnahme!\nDas Ergebnis wird kurz vor dem Spiel hier bekanntgegeben.\n\nLust auf mehr <b>Fan Feeling</b>? In unserer neuen App bauen wir eine neue <b>Fußball Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.");
		}

		BigInteger playerOfTheMatch(final BigInteger clientId, final int teamId) throws Exception {
			final JsonNode matchDays = get("team=" + teamId + "&season=" + currentSeason());
			if (matchDays != null) {
				final JsonNode clientJson = new ObjectMapper()
						.readTree(repository.one(Client.class, clientId).getStorage());
				for (int i = 0; i < matchDays.size(); i++) {
					if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
						final Instant startDate = Instant
								.ofEpochSecond(matchDays.get(i).get("fixture").get("timestamp").asLong());
						if (startDate.plus(Duration.ofHours(8)).isAfter(Instant.now())
								&& startDate.plus(Duration.ofHours(2)).isBefore(Instant.now())) {
							final QueryParams params = new QueryParams(Query.misc_listMarketing);
							params.setSearch(
									"clientMarketing.startDate=cast('" + startDate
											+ "' as timestamp) and clientMarketing.clientId="
											+ clientId);
							if (repository.list(params).size() > 0)
								break;
							final JsonNode matchDay = get("id=" + matchDays.get(i).get("fixture").get("id").asText())
									.get(0);
							if (matchDay != null && matchDay.findPath("players").get(0) != null) {
								JsonNode players = matchDay.findPath("players");
								players = players.get(players.get(0).get("team").get("id").asInt() == teamId ? 0 : 1)
										.get("players");
								final ObjectNode poll = new ObjectMapper().createObjectNode();
								poll.put("type", "PlayerOfTheMatch");
								poll.put("home", matchDay.findPath("home").get("logo").asText());
								poll.put("away", matchDay.findPath("away").get("logo").asText());
								poll.put("homeName",
										matchDay.findPath("home").get("name").asText().replace("Munich", "München"));
								poll.put("awayName",
										matchDay.findPath("away").get("name").asText().replace("Munich", "München"));
								poll.put("homeId", matchDay.findPath("home").get("id").asInt());
								poll.put("awayId", matchDay.findPath("away").get("id").asInt());
								poll.put("league", matchDay.findPath("league").get("logo").asText());
								poll.put("timestamp", matchDay.findPath("fixture").get("timestamp").asLong());
								poll.put("fixtureId", matchDays.get(i).get("fixture").get("id").asLong());
								poll.put("venue", matchDay.findPath("fixture").get("venue").get("name").asText());
								poll.put("city", matchDay.findPath("fixture").get("venue").get("city").asText());
								poll.put("location",
										matchDay.findPath("teams").get("home").get("id").asInt() == teamId ? "home"
												: "away");
								poll.put("prolog",
										"Umfrage <b>Spieler des Spiels</b> zum "
												+ matchDay.findPath("league").get("name").asText() +
												" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
												+ poll.get("homeName").asText()
												+ " - "
												+ poll.get("awayName").asText()
												+ "</div>vom <b>"
												+ formatDate(matchDay.findPath("fixture").get("timestamp").asLong(),
														null)
												+ "</b>. Möchtest Du teilnehmen?");
								final ObjectNode question = poll.putArray("questions").addObject();
								question.put("question", "Wer war für Dich Spieler des Spiels?");
								playerOfTheMatchAddAnswers(question.putArray("answers"), players);
								final ClientMarketing clientMarketing = new ClientMarketing();
								clientMarketing.setStartDate(new Timestamp(startDate.toEpochMilli()));
								clientMarketing
										.setEndDate(new Timestamp(startDate.plus(Duration.ofHours(18)).toEpochMilli()));
								final String endDate = formatDate(
										clientMarketing.getEndDate().getTime() / 1000 + 10 * 60, null);
								clientMarketing.setClientId(clientId);
								clientMarketing.setImage(Attachment.createImage(".png",
										image.create(poll, "Spieler", repository.one(Client.class, clientId), null)));
								poll.put("epilog", "Lieben Dank für die Teilnahme!\nDas Ergebnis wird am " + endDate
										+ " hier bekanntgegeben.\n\nLust auf mehr <b>Fan Feeling</b>? In unserer neuen App bauen wir eine neue <b>Fußball Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.");
								clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
								repository.save(clientMarketing);
								notification.sendPoll(clientMarketing);
								externalService.publishOnFacebook(clientId,
										"Umfrage Spieler des Spiels " + getTeam(clientId, poll)
												+ getOponent(clientId, poll)
												+ "\n\nKlickt auf das Bild, um abzustimmen. Dort wird Eure Stimme erfasst und automatisch in die Wertung übernommen, die Ihr ab dem "
												+ endDate + " hier sehen könnt."
												+ (clientJson.has("publishingPostfix")
														? "\n\n" + clientJson.get("publishingPostfix").asText()
														: ""),
										"/rest/marketing/" + clientMarketing.getId());
								return clientMarketing.getId();
							}
						}
					}
				}
			}
			return null;
		}

		private void playerOfTheMatchAddAnswers(final ArrayNode answers, final JsonNode players) {
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
					answers.addObject().put("answer", s);
				}
			}
		}

		public synchronized ClientMarketingResult result(final BigInteger clientMarketingId) throws Exception {
			final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(
					repository.one(ClientMarketing.class, clientMarketingId).getStorage()));
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketingId);
			params.setLimit(0);
			Result result = repository.list(params);
			final ClientMarketingResult clientMarketingResult;
			if (result.size() == 0) {
				clientMarketingResult = new ClientMarketingResult();
				clientMarketingResult.setClientMarketingId(clientMarketingId);
			} else
				clientMarketingResult = repository.one(ClientMarketingResult.class,
						(BigInteger) result.get(0).get("clientMarketingResult.id"));
			params.setQuery(Query.contact_listMarketing);
			params.setSearch("contactMarketing.clientMarketingId=" + clientMarketingId);
			result = repository.list(params);
			final ObjectMapper om = new ObjectMapper();
			final ObjectNode json = om.createObjectNode();
			json.put("participants", result.size());
			json.put("finished", 0);
			for (int i2 = 0; i2 < result.size(); i2++) {
				final String answers = (String) result.get(i2).get("contactMarketing.storage");
				if (answers != null && answers.length() > 2) {
					json.put("finished", json.get("finished").asInt() + 1);
					om.readTree(answers).fields()
							.forEachRemaining(e -> {
								if (Integer.valueOf(e.getKey().substring(1)) < poll.get("questions").size()) {
									if (!json.has(e.getKey())) {
										json.set(e.getKey(), om.createObjectNode());
										final ArrayNode a = om.createArrayNode();
										for (int i3 = 0; i3 < poll.get("questions").get(
												Integer.valueOf(e.getKey().substring(1))).get("answers").size(); i3++)
											a.add(0);
										((ObjectNode) json.get(e.getKey())).set("a", a);
									}
									for (int i = 0; i < e.getValue().get("a").size(); i++) {
										final int index = e.getValue().get("a").get(i).asInt();
										final ArrayNode a = ((ArrayNode) json.get(e.getKey()).get("a"));
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
			}
			clientMarketingResult.setStorage(om.writeValueAsString(json));
			repository.save(clientMarketingResult);
			return clientMarketingResult;
		}

		String resultAndNotify(final BigInteger clientId) throws Exception {
			final JsonNode clientJson = new ObjectMapper()
					.readTree(repository.one(Client.class, clientId).getStorage());
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch(
					"clientMarketingResult.published=false and clientMarketing.endDate<=cast('" + Instant.now()
							+ "' as timestamp) and clientMarketing.clientId=" + clientId);
			final Result list = repository.list(params);
			String result = "";
			for (int i = 0; i < list.size(); i++) {
				final ClientMarketingResult clientMarketingResult = result(
						(BigInteger) list.get(i).get("clientMarketing.id"));
				final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(
						repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId())
								.getStorage()));
				clientMarketingResult.setPublished(true);
				if (new ObjectMapper().readTree(Attachment.resolve(clientMarketingResult.getStorage())).has("q0")) {
					String prefix;
					if ("Prediction".equals(poll.get("type").asText()))
						prefix = "Ergebnistipps";
					else
						prefix = "Spieler";
					clientMarketingResult.setImage(Attachment.createImage(".png",
							image.create(poll, prefix, repository.one(Client.class, clientId), clientMarketingResult)));
					repository.save(clientMarketingResult);
					notification.sendResult(repository.one(ClientMarketing.class,
							clientMarketingResult.getClientMarketingId()));
					if ("PlayerOfTheMatch".equals(poll.get("type").asText()))
						prefix = "Umfrage Spieler des Spiels";
					externalService.publishOnFacebook(clientId,
							"Resultat der \"" + prefix + "\" " + getTeam(clientId, poll) + getOponent(clientId, poll)
									+ (clientJson.has("publishingPostfix")
											? "\n\n" + clientJson.get("publishingPostfix").asText()
											: ""),
							"/rest/marketing/" + clientMarketingResult.getClientMarketingId() + "/result");
					result += clientMarketingResult.getId() + " ";
				} else
					repository.save(clientMarketingResult);
			}
			return result.trim();
		}

		private String updateMatchdays(final BigInteger clientId) throws Exception {
			final JsonNode json = new ObjectMapper()
					.readTree(Attachment.resolve(repository.one(Client.class, clientId).getStorage()));
			String result = "";
			if (json.has("survey")) {
				for (int i2 = 0; i2 < json.get("survey").size(); i2++) {
					final String label = "team=" + json.get("survey").get(i2).asInt() + "&season=" + currentSeason();
					final JsonNode matchDays = get(label);
					if (matchDays != null) {
						final QueryParams params = new QueryParams(Query.misc_listStorage);
						for (int i = 0; i < matchDays.size(); i++) {
							if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())
									&& Instant.ofEpochSecond(
											matchDays.get(i).get("fixture").get("timestamp").asLong())
											.plus(Duration.ofHours(6)).isBefore(Instant.now())) {
								params.setSearch("storage.label='" + STORAGE_PREFIX + label + "'");
								final Result list = repository.list(params);
								if (list.size() > 0) {
									final Storage storage = repository.one(Storage.class,
											(BigInteger) list.get(0).get("storage.id"));
									storage.historize();
									storage.setStorage("");
									repository.save(storage);
									get(label);
								}
								result += "|" + json.get("survey").get(i2).asInt();
								break;
							}
						}
					}
				}
			}
			return result.length() > 0 ? "\nmatchdays: " + result.substring(1) : result;
		}

		private int currentSeason() {
			final LocalDateTime now = LocalDateTime.now();
			return now.getYear() - (now.getMonth().getValue() < 6 ? 1 : 0);
		}

		private String getTeam(final BigInteger clientId, final JsonNode poll) {
			return poll.get("homeName").asText();
		}

		private String getOponent(final BigInteger clientId, final JsonNode poll) {
			return " gegen " + poll.get("awayName").asText()
					+ " in " + poll.get("city").asText() + " · " + poll.get("venue").asText()
					+ " am " + formatDate(poll.get("timestamp").asLong(), null) + ".";
		}

		private String getLine(final int x, final String singular, final String plural) {
			return "<br/>" + x + (x > 1 ? plural : singular);
		}
	}

	private class Image {
		private final int width = 600, height = 315, padding = 20;

		private byte[] create(final JsonNode poll, final String subtitlePrefix, final Client client,
				final ClientMarketingResult clientMarketingResult) throws Exception {
			final JsonNode json = new ObjectMapper().readTree(Attachment.resolve(client.getStorage())).get("css");
			final String[] color1 = json.get("bg1stop").asText().replace("rgb(", "").replace(")", "").split(",");
			final String[] color2 = json.get("bg1start").asText().replace("rgb(", "").replace(")", "").split(",");
			final String[] color3 = json.get("text").asText().replace("rgb(", "").replace(")", "").split(",");
			final String urlLeague = poll.get("league").asText();
			final String urlHome = poll.findPath("home").asText();
			final String urlAway = poll.findPath("away").asText();
			final boolean homeMatch = "home".equals(poll.findPath("location").asText());
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
					clientMarketingResult == null && "PlayerOfTheMatch".equals(poll.get("type").asText()) ? 1 : 0.15f));
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
				s = subtitlePrefix + " des Spiels vom " + formatDate(poll.get("timestamp").asLong(), "d.M.yyyy H:mm");
				g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));
				g2.setFont(customFont.deriveFont(12f));
				s = "© " + LocalDateTime.now().getYear() + " " + client.getName();
				g2.drawString(s, width - g2.getFontMetrics().stringWidth(s) - padding, height - padding);
				if (clientMarketingResult != null)
					result(g2, customFont, poll,
							new ObjectMapper().readTree(Attachment.resolve(clientMarketingResult.getStorage())),
							colorText);
				else if ("Prediction".equals(poll.get("type").asText()))
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

		private void prediction(final Graphics2D g2, final Font customFont, final JsonNode poll, final Color colorText,
				final BigInteger clientId) throws Exception {
			g2.setFont(customFont.deriveFont(12f));
			final int h = g2.getFontMetrics().getHeight();
			int y = padding;
			final double w = width * 0.3, delta = 1.6;
			for (int i = 0; i < poll.get("matches").size(); i++) {
				final String[] s = poll.get("matches").get(poll.get("matches").size() - i - 1).asText().split("\\|");
				g2.drawString(s[0], width - padding - 120 - g2.getFontMetrics().stringWidth(s[0]) / 2, y + h);
				g2.drawString(s[1], width - padding - g2.getFontMetrics().stringWidth(s[1]), y + h);
				y += delta * padding;
			}
			y = padding;
			if (poll.get("statistics").size() == 0) {
				final String[] s = new String[4];
				s[0] = "Das erste Spiel seit langem zwischen";
				s[1] = poll.get("homeName").asText() + " : " + poll.get("awayName").asText();
				s[2] = "in " + poll.get("city").asText() + " · " + poll.get("venue").asText();
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
				for (int i = 0; i < poll.get("statistics").size() && i < 9; i++) {
					if (!poll.get("statistics").get(i).get("label").asText().toLowerCase().contains("passes")) {
						max = Math.max(max, poll.get("statistics").get(i).get("home").asDouble());
						max = Math.max(max, poll.get("statistics").get(i).get("away").asDouble());
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
				for (int i = 0; i < poll.get("statistics").size() && i < 9; i++) {
					final double w2 = poll.get("statistics").get(i).get("home").asDouble() / max * w;
					g2.setColor(new Color(255, 100, 0, 50));
					g2.fillRect(padding + (int) (w - w2), y - 4, (int) w2, h * 2);
					g2.setColor(new Color(0, 100, 255, 50));
					g2.fillRect(padding + (int) w, y - 4,
							(int) (poll.get("statistics").get(i).get("away").asDouble() / max * w),
							h * 2);
					g2.setColor(colorText);
					String s = poll.get("statistics").get(i).get("label").asText();
					if (labels.containsKey(s))
						s = labels.get(s);
					g2.drawString(s, padding + (int) w - g2.getFontMetrics().stringWidth(s) / 2, y + h);
					s = poll.get("statistics").get(i).get("home").asText();
					g2.drawString(s, padding + (int) w * 0.4f - g2.getFontMetrics().stringWidth(s), y + h);
					s = poll.get("statistics").get(i).get("away").asText();
					g2.drawString(s, padding + (int) w * 1.6f, y + h);
					y += delta * padding;
				}
			}
		}

		private void result(final Graphics2D g2, final Font customFont, JsonNode poll, JsonNode result,
				final Color colorText) throws Exception {
			g2.setFont(customFont.deriveFont(16f));
			final int h = g2.getFontMetrics().getHeight();
			final int total = result.get("participants").asInt();
			int y = padding;
			final boolean prediction = "Prediction".equals(poll.get("type").asText());
			poll = poll.get("questions").get(0).get("answers");
			final String text = result.get("q0").has("t") ? result.get("q0").get("t").asText() : null;
			result = result.get("q0").get("a");
			final List<String> x = new ArrayList<>();
			final String leftPad = "000000000000000";
			for (int i = 0; i < result.size() - (prediction ? 1 : 0); i++)
				x.add(leftPad.substring(result.get(i).asText().length()) + result.get(i).asText() + "_"
						+ poll.get(i).get("answer").asText());
			if (prediction) {
				if (text != null) {
					final Pattern predictionText = Pattern.compile("(\\d+)[ ]*[ :-]+[ ]*(\\d+)", Pattern.MULTILINE);
					final String[] answers = text.substring(5, text.length() - 6).split("</div><div>");
					final Map<String, Integer> results = new HashMap<>();
					for (final String s : answers) {
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

	private class Notification {
		private void sendPoll(final ClientMarketing clientMarketing)
				throws Exception {
			final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(clientMarketing.getStorage()));
			final QueryParams params = new QueryParams(Query.contact_listId);
			String s = "contact.verified=true and contact.clientId=" + clientMarketing.getClientId()
					+ " and cast(REGEXP_LIKE(contact.skills,'9." + poll.get("homeId").asText()
					+ "|9." + poll.get("awayId").asText() + "') as integer)=1";
			if (!Strings.isEmpty(clientMarketing.getLanguage())) {
				String s2 = "";
				final String[] langs = clientMarketing.getLanguage().split(Attachment.SEPARATOR);
				for (int i = 0; i < langs.length; i++)
					s2 += " or contact.language='" + langs[i] + "'";
				s += " and (" + s2.substring(4) + ")";
			}
			if (!Strings.isEmpty(clientMarketing.getGender())) {
				String s2 = "";
				final String[] genders = clientMarketing.getGender().split(Attachment.SEPARATOR);
				for (int i = 0; i < genders.length; i++)
					s2 += " or contact.gender=" + genders[i];
				s += " and (" + s2.substring(4) + ")";
			}
			if (!Strings.isEmpty(clientMarketing.getAge()))
				s += " and (contact.age>=" + clientMarketing.getAge().split(",")[0] + " and contact.age<="
						+ clientMarketing.getAge().split(",")[1] + ")";
			params.setSearch(s);
			final Result users = repository.list(params);
			if (!Strings.isEmpty(clientMarketing.getRegion())) {
				for (int i = users.size() - 1; i >= 0; i--) {
					params.setQuery(Query.contact_listGeoLocationHistory);
					params.setSearch("contactGeoLocationHistory.contactId=" + users.get(i).get("contact.id"));
					final Result result = repository.list(params);
					if (result.size() > 0) {
						final GeoLocation geoLocation = repository.one(GeoLocation.class,
								(BigInteger) result.get(0).get("contactGeoLocationHistory.geoLocationId"));
						if ((Strings.isEmpty(geoLocation.getTown())
								&& Strings.isEmpty(geoLocation.getCountry())
								&& Strings.isEmpty(geoLocation.getZipCode()))
								|| (!clientMarketing.getRegion().contains(geoLocation.getTown())
										&& !clientMarketing.getRegion()
												.contains(" " + geoLocation.getCountry() + " ")))
							users.getList().remove(i);
						else {
							boolean remove = true;
							for (int i2 = geoLocation.getZipCode().length(); i2 > 1; i2--) {
								if (clientMarketing.getRegion().contains(
										geoLocation.getCountry() + "-" + geoLocation.getZipCode().substring(0, i))) {
									remove = false;
									break;
								}
							}
							if (remove)
								users.getList().remove(i);
						}
					}
				}
			}
			send(users, clientMarketing, ContactNotificationTextType.clientMarketing, "contact.id");
		}

		private void sendResult(final ClientMarketing clientMarketing) throws Exception {
			final QueryParams params = new QueryParams(Query.contact_listMarketing);
			params.setSearch(
					"contactMarketing.finished=true and contactMarketing.contactId is not null and contactMarketing.clientMarketingId="
							+ clientMarketing.getId());
			send(repository.list(params), clientMarketing, ContactNotificationTextType.clientMarketingResult,
					"contactMarketing.contactId");
		}

		private void send(final Result users, final ClientMarketing clientMarketing,
				final ContactNotificationTextType type, final String field) throws Exception {
			final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(clientMarketing.getStorage()));
			final List<Object> sent = new ArrayList<>();
			final TextId textId = "PlayerOfTheMatch".equals(poll.get("type").asText())
					? TextId.marketing_playerOfTheMatch
					: TextId.marketing_prediction;
			for (int i = 0; i < users.size(); i++) {
				if (!sent.contains(users.get(i).get(field))) {
					final Contact contact = repository.one(Contact.class, (BigInteger) users.get(i).get(field));
					notificationService.sendNotification(null,
							contact,
							type, "m=" + clientMarketing.getId(),
							text.getText(contact, textId).replace("{0}", poll.get("homeName").asText() + " : " +
									poll.get("awayName").asText()));
					sent.add(users.get(i).get(field));
				}
			}
		}
	}

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		final Result list = repository.list(new QueryParams(Query.misc_listClient));
		for (int i = 0; i < list.size(); i++) {
			try {
				final BigInteger clientId = (BigInteger) list.get(i).get("client.id");
				final JsonNode json = new ObjectMapper().readTree(list.get(i).get("client.storage").toString());
				if (json.has("survey")) {
					for (int i2 = 0; i2 < json.get("survey").size(); i2++) {
						final int teamId = json.get("survey").get(i2).asInt();
						System.out.println("update " + clientId + " - " + teamId);
						BigInteger id = synchronize.prediction(clientId, teamId);
						if (id != null)
							result.result += "\nprediction: " + id;
						id = synchronize.playerOfTheMatch(clientId, teamId);
						if (id != null)
							result.result += "\npoll: " + id;
						final String s = synchronize.resultAndNotify(clientId);
						if (s.length() > 0)
							result.result += "\nresultAndNotify: " + s;
						result.result += synchronize.updateMatchdays(clientId);

					}
				}
			} catch (final Exception e) {
				notificationService.createTicket(TicketType.ERROR, "Survey", Strings.stackTraceToString(e),
						(BigInteger) list.get(i).get("client.id"));
				if (result.exception == null)
					result.exception = e;
			}
		}
		return result;
	}

	public void synchronizeResult(final BigInteger clientMarketingId) throws Exception {
		synchronize.result(clientMarketingId);
	}

	private String formatDate(final long seconds, final String format) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(seconds * 1000),
				TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId())
				.format(DateTimeFormatter.ofPattern(format == null ? "d.M.yyyy' um 'H:mm' Uhr'" : format));
	}

	protected JsonNode get(final String url) throws Exception {
		JsonNode fixture = null;
		final String label = STORAGE_PREFIX + url;
		final QueryParams params = new QueryParams(Query.misc_listStorage);
		params.setSearch("storage.label='" + label + "'");
		final Result result = repository.list(params);
		if (result.size() > 0 && !Strings.isEmpty(result.get(0).get("storage.storage")))
			fixture = new ObjectMapper().readTree(result.get(0).get("storage.storage").toString());
		if (fixture == null || fixture.get("results").intValue() == 0
				|| fixture.has("errors") && fixture.get("errors").has("rateLimit")) {
			fixture = WebClient
					.create("https://v3.football.api-sports.io/fixtures?" + url)
					.get()
					.header("x-rapidapi-key", token)
					.header("x-rapidapi-host", "v3.football.api-sports.io")
					.retrieve()
					.toEntity(JsonNode.class).block().getBody();
			if (fixture != null && fixture.get("response") != null) {
				final Storage storage = result.size() == 0 ? new Storage()
						: repository.one(Storage.class, (BigInteger) result.get(0).get("storage.id"));
				storage.setLabel(label);
				storage.setStorage(new ObjectMapper().writeValueAsString(fixture));
				repository.save(storage);
			} else
				notificationService.createTicket(TicketType.ERROR, "FIXTURE not FOUND", url, null);
		}
		if (fixture == null)
			throw new RuntimeException(url + " not found");
		return fixture.get("response");
	}
}
