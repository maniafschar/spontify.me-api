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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

import javax.imageio.ImageIO;

import org.apache.batik.ext.awt.RadialGradientPaint;
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
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketing.ClientMarketingMode;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;

@Service
public class SurveyService {
	private final static Map<BigInteger, Integer> clients = new HashMap<>();
	private final static AtomicLong lastRun = new AtomicLong(0);
	private final static int FIRST_YEAR = 2010;
	private final Synchonize synchronize = new Synchonize();
	private final Image image = new Image();
	private final Notification notification = new Notification();
	public final Test test = new Test();

	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Value("${app.sports.api.token}")
	private String token;

	static {
		clients.put(BigInteger.valueOf(4), 157);
	}

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		clients.keySet().forEach(e -> {
			try {
				result.result = synchronize.matchdays(e, clients.get(e));
				BigInteger id = synchronize.poll(e, clients.get(e));
				if (id != null)
					result.result += "\npoll: " + id;
				String s = synchronize.resultAndNotify(e);
				if (s.length() > 0)
					result.result += "\nresultAndNotify: " + s;
				s = synchronize.history(e, clients.get(e));
				if (s.length() > 0)
					result.result += "\nhistory: " + s;
				id = synchronize.prediction(e, clients.get(e));
				if (id != null)
					result.result += "\nprediction: " + id;
			} catch (final Exception ex) {
				result.exception = ex;
			}
		});
		return result;
	}

	public void synchronizeResult(final BigInteger clientMarketingId) throws Exception {
		synchronize.result(clientMarketingId);
	}

	public class Test {
		public BigInteger poll(final boolean prediction) throws Exception {
			final int teamId = 0;
			lastRun.set(0);
			if (prediction) {
				final QueryParams params = new QueryParams(Query.misc_listStorage);
				params.setSearch("storage.label='football-" + teamId + "-" + LocalDateTime.now().getYear() + "'");
				if (repository.list(params).size() == 0) {
					final Storage storage = new Storage();
					storage.setLabel("football-" + teamId + "-" + LocalDateTime.now().getYear());
					storage.setStorage(new ObjectMapper().writeValueAsString(
							get("team=" + teamId + "&")));
					repository.save(storage);
				}
				return synchronize.prediction(BigInteger.ONE, teamId);
			}
			synchronize.matchdays(BigInteger.ONE, teamId);
			return synchronize.poll(BigInteger.ONE, teamId);
		}

		public String result(final BigInteger clientMarketingId) throws Exception {
			final ClientMarketing clientMarketing = repository.one(ClientMarketing.class, clientMarketingId);
			final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(clientMarketing.getStorage()));
			final int max = (int) (10 + Math.random() * 100);
			for (int i = 0; i < max; i++) {
				final ContactMarketing contactMarketing = new ContactMarketing();
				contactMarketing.setClientMarketingId(clientMarketingId);
				contactMarketing.setContactId(BigInteger.ONE);
				contactMarketing.setFinished(Boolean.TRUE);
				contactMarketing.setStorage("{\"q1\":{\"a\":["
						+ (int) (Math.random() * poll.get("questions").get(0).get("answers").size()) + "]}}");
				repository.save(contactMarketing);
			}
			clientMarketing.setEndDate(new Timestamp(System.currentTimeMillis() - 1000));
			repository.save(clientMarketing);
			return synchronize.resultAndNotify(clientMarketing.getClientId());
		}
	}

	private class Synchonize {
		private String history(final BigInteger clientId, final int teamId) throws Exception {
			String result = "";
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			for (int i = FIRST_YEAR; i <= LocalDateTime.now().getYear(); i++) {
				final String label = "football-" + teamId + "-" + i;
				params.setSearch("storage.label='" + label + "'");
				if (repository.list(params).size() == 0) {
					final Storage storage = new Storage();
					storage.setLabel(label);
					storage.setStorage(new ObjectMapper().writeValueAsString(get(
							"https://v3.football.api-sports.io/fixtures?team=" + teamId + "&season=" + i)));
					repository.save(storage);
					result += label + " ";
				}
			}
			return result.trim();
		}

		private String matchdays(final BigInteger clientId, final int teamId) throws Exception {
			if (System.currentTimeMillis() - lastRun.get() < 24 * 60 * 60 * 1000)
				return "Matchdays already run in last 24 hours";
			lastRun.set(System.currentTimeMillis());
			int count = 0;
			final QueryParams params = new QueryParams(Query.misc_listMarketing);
			params.setSearch(
					"clientMarketing.startDate>'" + Instant.now() + "' and clientMarketing.clientId=" + clientId +
							" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
			if (repository.list(params).size() == 0) {
				final JsonNode matchDays = get("team=" + teamId + "&season=" + LocalDateTime.now().getYear());
				if (matchDays != null) {
					for (int i = 0; i < matchDays.size(); i++) {
						if ("NS".equals(matchDays.get(i).get("fixture").get("status").get("short").asText())) {
							final ClientMarketing clientMarketing = new ClientMarketing();
							clientMarketing.setStartDate(
									new Timestamp(Instant
											.ofEpochSecond(matchDays.get(i).get("fixture").get("timestamp").asLong())
											.plus(Duration.ofHours(2)).toEpochMilli()));
							clientMarketing.setEndDate(
									new Timestamp(Instant.ofEpochMilli(clientMarketing.getStartDate().getTime())
											.plus(Duration.ofDays(1)).toEpochMilli()));
							clientMarketing.setClientId(clientId);
							clientMarketing.setStorage(matchDays.get(i).get("fixture").get("id").asText());
							clientMarketing.setMode(teamId == 0 ? ClientMarketingMode.Test
									: ClientMarketingMode.Live);
							repository.save(clientMarketing);
							count++;
						}
					}
				}
			}
			return "Matchdays: " + count;
		}

		private BigInteger prediction(final BigInteger clientId, final int teamId) throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			params.setSearch("storage.label='football-" + teamId + "-" + LocalDateTime.now().getYear() + "'");
			final Result result = repository.list(params);
			if (result.size() > 0) {
				final JsonNode json = new ObjectMapper().readTree(result.get(0).get("storage.storage").toString())
						.get("response");
				for (int i = 0; i < json.size(); i++) {
					if ("NS".equals(json.get(i).get("fixture").get("status").get("short").asText())) {
						final Instant date = Instant
								.ofEpochSecond(json.get(i).get("fixture").get("timestamp").asLong());
						if (date.minus(Duration.ofDays(1)).minus(Duration.ofHours(12)).isBefore(Instant.now())) {
							final ObjectNode poll = new ObjectMapper().createObjectNode();
							poll.put("home", json.get(i).get("teams").get("home").get("logo").asText());
							poll.put("away", json.get(i).get("teams").get("away").get("logo").asText());
							poll.put("homeName", json.get(i).get("teams").get("home").get("name").asText().replace("Munich", "München"));
							poll.put("awayName", json.get(i).get("teams").get("away").get("name").asText().replace("Munich", "München"));
							poll.put("homeId", json.get(i).get("teams").get("home").get("id").asInt());
							poll.put("awayId", json.get(i).get("teams").get("away").get("id").asInt());
							poll.put("league", json.get(i).get("league").get("logo").asText());
							poll.put("leagueName", json.get(i).get("league").get("name").asText());
							poll.put("timestamp", json.get(i).get("fixture").get("timestamp").asLong());
							poll.put("venue", json.get(i).get("fixture").get("venue").get("name").asText());
							poll.put("city", json.get(i).findPath("fixture").get("venue").get("city").asText());
							poll.put("location",
									json.get(i).get("teams").get("home").get("id").asInt() == teamId ? "home" : "away");
							final Instant end = Instant.ofEpochSecond(poll.get("timestamp").asLong())
									.minus(Duration.ofHours(1));
							params.setQuery(Query.misc_listMarketing);
							params.setSearch(
									"clientMarketing.endDate='" + end.toString() + "' and clientMarketing.clientId="
											+ clientId);
							if (repository.list(params).size() == 0) {
								predictionAddStatistics(clientId, poll);
								final ClientMarketing clientMarketing = new ClientMarketing();
								clientMarketing.setClientId(clientId);
								clientMarketing
										.setStartDate(new Timestamp(end
												.minus(Duration.ofDays(1)).minus(Duration.ofHours(12)).toEpochMilli()));
								clientMarketing.setEndDate(new Timestamp(end.toEpochMilli()));
								clientMarketing.setStorage(new ObjectMapper().writeValueAsString(poll));
								clientMarketing.setMode(ClientMarketingMode.Live);
								clientMarketing.setImage(Attachment.createImage(".png",
										image.create(poll, "Ergebnistipps", repository.one(Client.class, clientId).getName(),
												null)));
								repository.save(clientMarketing);
								notification.sendPoll(clientMarketing);
								publish(clientId,
										"Ergebnistipps für das Spiels " + poll.get("homeName").asText() + " - "
												+ poll.get("awayName").asText()
												+ " am " + formatDate(poll.get("timestamp").asLong()),
										"/rest/action/marketing/init/" + clientMarketing.getId());
								return clientMarketing.getId();
							}
						}
						break;
					}
				}
			}
			return null;
		}

		private void predictionAddStatistics(final BigInteger clientId, final ObjectNode poll)
				throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listStorage);
			final ObjectMapper om = new ObjectMapper();
			final int teamId = poll.get(poll.get("location").asText() + "Id").asInt();
			final ArrayNode matches = om.createArrayNode();
			for (int i = FIRST_YEAR; i <= LocalDateTime.now().getYear(); i++) {
				params.setSearch("storage.label='football-" + teamId + "-" + i + "'");
				final Result result = repository.list(params);
				if (result.size() > 0) {
					final JsonNode json = om.readTree(result.get(0).get("storage.storage").toString()).get("response");
					for (int i2 = 0; i2 < json.size(); i2++) {
						if (json.get(i2).get("teams").get("home").get("id").asInt() == poll.get("homeId").asInt()
								&& json.get(i2).get("teams").get("away").get("id").asInt() == poll.get("awayId")
										.asInt()
								&& "FT".equals(json.get(i2).get("fixture").get("status").get("short").asText())) {
							final JsonNode fixture = get("id=" + json.get(i2).get("fixture").get("id").asInt());
							if (fixture.has("statistics") && fixture.get("statistics").size() > 1
									&& fixture.get("statistics").get(0).has("statistics")) {
								final ObjectNode match = om.createObjectNode();
								match.put("timestamp", fixture.get("fixture").get("timestamp").asLong());
								match.set("goals", fixture.get("goals"));
								match.set("statistics", fixture.get("statistics"));
								matches.add(match);
							}
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
				((ArrayNode) poll.get("matches"))
						.add(s + "|" + formatDate(matches.get(i).get("timestamp").asLong()));
			}
			final ArrayNode statistics = om.createArrayNode();
			for (int i = 0; i < labels.size(); i++) {
				final ObjectNode row = om.createObjectNode();
				row.put("home", homeList.get(labels.get(i)) / matches.size());
				row.put("away", awayList.get(labels.get(i)) / matches.size());
				row.put("label", labels.get(i));
				statistics.add(row);
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
									.replace("Munich", "München")
							+ " - "
							+ poll.get("awayName").asText().replace("Munich",
									"München")
							+ "</div>vom <b>"
							+ formatDate(poll.get("timestamp").asLong())
							+ "</b>. Möchtest Du teilnehmen?");
			poll.put("epilog",
					"Lieben Dank für die Teilnahme!\n\nLust auf mehr <b>Bayern Feeling</b>? In unserer neuen App bauen wir eine reine Bayern <b>Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.");
		}

		private BigInteger poll(final BigInteger clientId, final int teamId) throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listMarketing);
			params.setSearch(
					"clientMarketing.startDate<='" + Instant.now() + "' and clientMarketing.endDate>'" + Instant.now() +
							"' and clientMarketing.clientId=" + clientId +
							" and clientMarketing.storage not like '%" + Attachment.SEPARATOR + "%'");
			final Result list = repository.list(params);
			if (list.size() > 0) {
				final JsonNode matchDay = get("id=" + list.get(0).get("clientMarketing.storage"));
				if (matchDay != null) {
					JsonNode e = matchDay.findPath("players");
					e = e.get(e.get(0).get("team").get("id").asInt() == teamId ? 0 : 1).get("players");
					final ObjectNode poll = new ObjectMapper().createObjectNode();
					poll.put("home", matchDay.findPath("home").get("logo").asText());
					poll.put("away", matchDay.findPath("away").get("logo").asText());
					poll.put("homeName", matchDay.findPath("home").get("name").asText().replace("Munich", "München"));
					poll.put("awayName", matchDay.findPath("away").get("name").asText().replace("Munich", "München"));
					poll.put("league", matchDay.findPath("league").get("logo").asText());
					poll.put("timestamp", matchDay.findPath("fixture").get("timestamp").asLong());
					poll.put("venue", matchDay.findPath("fixture").get("venue").get("name").asText());
					poll.put("city", matchDay.findPath("fixture").get("venue").get("city").asText());
					poll.put("location",
							matchDay.findPath("teams").get("home").get("id").asInt() == teamId ? "home" : "away");
					poll.put("prolog",
							"Umfrage <b>Spieler des Spiels</b> zum "
									+ matchDay.findPath("league").get("name").asText() +
									" Spiel<div style=\"padding:1em 0;font-weight:bold;\">"
									+ poll.put("homeName").asText()
									+ " - "
									+ poll.get("awayName").asText()
									+ "</div>vom <b>"
									+ formatDate(matchDay.findPath("fixture").get("timestamp").asLong())
									+ "</b>. Möchtest Du teilnehmen?");
					poll.put("epilog",
							"Lieben Dank für die Teilnahme!\n\nLust auf mehr <b>Bayern Feeling</b>? In unserer neuen App bauen wir eine reine Bayern <b>Fan Community</b> auf.\n\nMit ein paar wenigen Klicks kannst auch Du dabei sein.");
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
								s += getLine(statistics.get("shots").get("total").asInt(), " Torschuss, ",
										" Torschüsse, ")
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
							image.create(poll, "Spieler", repository.one(Client.class, clientId).getName(), null)));
					repository.save(clientMarketing);
					notification.sendPoll(clientMarketing);
					publish(clientId,
							"Umfrage Spieler des Spiels " + poll.get("homeName").asText() + " - "
									+ poll.get("awayName").asText()
									+ " vom " + formatDate(poll.get("timestamp").asLong()),
							"/rest/action/marketing/init/" + clientMarketing.getId());
				}
				return (BigInteger) list.get(0).get("clientMarketing.id");
			}
			return null;
		}

		private String getLine(final int x, final String singular, final String plural) {
			return "<br/>" + x + (x > 1 ? plural : singular);
		}

		private String resultAndNotify(final BigInteger clientId) throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.published=false and clientMarketing.endDate<='" + Instant.now()
					+ "' and clientMarketing.clientId=" + clientId);
			final Result list = repository.list(params);
			String result = "";
			for (int i = 0; i < list.size(); i++) {
				final ClientMarketingResult clientMarketingResult = result(
						(BigInteger) list.get(0).get("clientMarketing.id"));
				final JsonNode poll = new ObjectMapper().readTree(Attachment.resolve(
						repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId())
								.getStorage()));
				clientMarketingResult.setImage(Attachment.createImage(".png",
						image.create(poll, "Spieler", repository.one(Client.class, clientId).getName(),
								clientMarketingResult)));
				clientMarketingResult.setPublished(true);
				repository.save(clientMarketingResult);
				notification.sendResult(
						repository.one(ClientMarketing.class, clientMarketingResult.getClientMarketingId()));
				publish(clientId,
						"Ergebnis der Umfrage Spieler des Spiels " + poll.get("homeName").asText() + " - "
								+ poll.get("awayName").asText()
								+ " vom " + formatDate(poll.get("timestamp").asLong()),
						"/rest/action/marketing/result/" + clientMarketingResult.getClientMarketingId());
				result += clientMarketingResult.getId() + " ";
			}
			return result.trim();
		}

		public synchronized ClientMarketingResult result(final BigInteger clientMarketingId) throws Exception {
			final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
			params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketingId);
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
				if (answers != null && answers.length() > 2)
					om.readTree(answers).fields()
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
										for (int i3 = a.size(); i3 <= index; i3++)
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
	}

	private class Image {
		private final int width = 600, height = 315, padding = 20;

		private byte[] create(final JsonNode poll, final String subtitlePrefix, final String copyright,
				final ClientMarketingResult clientMarketingResult) throws Exception {
			final String urlLeague = poll.get("league").asText();
			final String urlHome = poll.findPath("home").asText();
			final String urlAway = poll.findPath("away").asText();
			final boolean homeMatch = "home".equals(poll.findPath("location").asText());
			final BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			final Graphics2D g2 = output.createGraphics();
			g2.setComposite(AlphaComposite.Src);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			final RadialGradientPaint gradient = new RadialGradientPaint(width / 2 - 2 * padding,
					height / 2 - 2 * padding,
					height,
					new float[] { .3f, 1f },
					new Color[] { new Color(245, 239, 232), new Color(246, 194, 166) });
			g2.setPaint(gradient);
			g2.fill(new Rectangle2D.Float(0, 0, width, height));
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
					clientMarketingResult == null && !poll.has("homeId") ? 1 : 0.1f));
			final int h = (int) (height * 0.4);
			if (!homeMatch)
				draw(urlHome, g2, width / 2, padding, h, -1);
			draw(urlAway, g2, width / 2, padding, h, 1);
			if (homeMatch)
				draw(urlHome, g2, width / 2, padding, h, -1);
			draw(urlLeague, g2, width - padding, padding, height / 4, 0);
			final Font customFont = Font
					.createFont(Font.TRUETYPE_FONT, getClass().getResourceAsStream("/Comfortaa-Regular.ttf"))
					.deriveFont(50f);
			GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(customFont);
			g2.setFont(customFont);
			g2.setColor(Color.BLACK);
			String s = "Umfrage";
			if (clientMarketingResult != null)
				s += "ergebnis";
			g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 14.5f);
			g2.setFont(customFont.deriveFont(24f));
			s = subtitlePrefix + " des Spiels vom " + formatDate(poll.get("timestamp").asLong());
			g2.drawString(s, (width - g2.getFontMetrics().stringWidth(s)) / 2, height / 20 * 17.5f);
			g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));
			g2.setFont(customFont.deriveFont(12f));
			s = "© " + LocalDateTime.now().getYear() + " " + copyright;
			g2.drawString(s, width - g2.getFontMetrics().stringWidth(s) - padding, height - padding);
			if (clientMarketingResult != null)
				result(g2, customFont, poll,
						new ObjectMapper().readTree(Attachment.resolve(clientMarketingResult.getStorage())));
			else if (poll.has("homeId"))
				prediction(g2, customFont, poll);
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

		private void prediction(final Graphics2D g2, final Font customFont, final JsonNode poll)
				throws Exception {
			g2.setFont(customFont.deriveFont(12f));
			final int h = g2.getFontMetrics().getHeight();
			int y = padding;
			final double w = width * 0.3, delta = 1.6;
			g2.setColor(new Color(0, 0, 0));
			for (int i = 0; i < poll.get("matches").size() && i < 8; i++) {
				final String[] s = poll.get("matches").get(poll.get("matches").size() - i - 1).asText().split("|");
				g2.drawString(s[0], width - padding - 150 - g2.getFontMetrics().stringWidth(s[0]) / 2, y + h);
				g2.drawString(s[1], width - padding - g2.getFontMetrics().stringWidth(s[1]), y + h);
				y += delta * padding;
			}
			y = padding;
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
				g2.setColor(new Color(0, 0, 0));
				String s = poll.get("statistics").get(i).get("label").asText();
				if (labels.containsKey(s))
					s = labels.get(s);
				g2.drawString(s, padding + (int) w - g2.getFontMetrics().stringWidth(s) / 2, y + h);
				s = poll.get("statistics").get(i).get("home").asText();
				g2.drawString(s, padding + (int) w / 2 - g2.getFontMetrics().stringWidth(s), y + h);
				s = poll.get("statistics").get(i).get("away").asText();
				g2.drawString(s, padding + (int) w * 1.5f, y + h);
				y += delta * padding;
			}
		}

		private void result(final Graphics2D g2, final Font customFont, JsonNode poll, JsonNode result)
				throws Exception {
			g2.setFont(customFont.deriveFont(16f));
			final int h = g2.getFontMetrics().getHeight();
			final int total = result.get("participants").asInt();
			int y = padding;
			poll = poll.get("questions").get(0).get("answers");
			result = result.get("q1").get("a");
			final List<String> x = new ArrayList<>();
			final String leftPad = "000000000000000";
			for (int i = 0; i < result.size(); i++)
				x.add(leftPad.substring(result.get(i).asText().length()) + result.get(i).asText() + "_"
						+ poll.get(i).get("answer").asText());
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
				g2.setColor(new Color(0, 0, 0));
				g2.drawString(percent + "%", padding * 1.8f, y + h + 5);
				g2.drawString(s[1], padding * 4.5f, y + h + 5);
				y += 2.3 * padding;
			}
		}

		private void draw(final String url, final Graphics2D g, final int x, final int y, final int height,
				final int pos) throws Exception {
			final BufferedImage image = ImageIO.read(new URL(url).openStream());
			final int paddingLogos = -10;
			final int w = image.getWidth() / image.getHeight() * height;
			g.drawImage(image, x - (pos == 1 ? 0 : w) + pos * paddingLogos, y,
					x + (pos == 1 ? w : 0) + pos * paddingLogos,
					height + y, 0, 0, image.getWidth(), image.getHeight(), null);
		}
	}

	private class Notification {
		private void sendPoll(final ClientMarketing clientMarketing) throws Exception {
			final QueryParams params = new QueryParams(Query.contact_listId);
			String s = "contact.verified=true and contact.clientId=" + clientMarketing.getClientId();
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

		private void send(final Result users, final ClientMarketing clientMarketing,
				final ContactNotificationTextType type,
				final String field) throws Exception {
			if (clientMarketing.getMode() == ClientMarketingMode.Test)
				return;
			final List<Object> sent = new ArrayList<>();
			for (int i = 0; i < users.size(); i++) {
				if (!sent.contains(users.get(i).get(field))) {
					notificationService.sendNotification(null,
							repository.one(Contact.class, (BigInteger) users.get(i).get(field)),
							type, "m=" + clientMarketing.getId());
					sent.add(users.get(i).get(field));
				}
			}
		}

		private void sendResult(final ClientMarketing clientMarketing) throws Exception {
			final QueryParams params = new QueryParams(Query.contact_listMarketing);
			params.setSearch(
					"contactMarketing.finished=true and contactMarketing.contactId is not null and contactMarketing.clientMarketingId="
							+ clientMarketing.getId());
			send(repository.list(params), clientMarketing, ContactNotificationTextType.clientMarketingResult,
					"contactMarketing.contactId");
		}
	}

	private void publish(final BigInteger clientId, final String message, final String link) throws Exception {
		final Client client = repository.one(Client.class, clientId);
		if (!Strings.isEmpty(client.getFbPageAccessToken()) && !Strings.isEmpty(client.getFbPageId())) {
			final Map<String, String> body = new HashMap<>();
			body.put("message", message);
			body.put("link", Strings.removeSubdomain(client.getUrl()) + link);
			body.put("access_token", client.getFbPageAccessToken());
			final String response = WebClient
					.create("https://graph.facebook.com/v18.0/" + client.getFbPageId() + "/feed")
					.post().bodyValue(body).retrieve()
					.toEntity(String.class).block().getBody();
			if (response == null || !response.contains("\"id\":"))
				notificationService.createTicket(TicketType.ERROR, "FB", response, null);
		}
	}

	private String formatDate(final long seconds) {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(seconds * 1000),
				TimeZone.getTimeZone(Strings.TIME_OFFSET).toZoneId())
				.format(DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"));
	}

	protected JsonNode get(final String url) throws Exception {
		if (url.contains("team=0&"))
			return new ObjectMapper().readTree(IOUtils
					.toString(getClass().getResourceAsStream("/surveyMatchdaysOne.json"),
							StandardCharsets.UTF_8)
					.replace("\"{date}\"", "" + (Instant.now().getEpochSecond() - 60 * 60 * 2 - 5)));
		if (url.equals("id=0"))
			return new ObjectMapper().readTree(getClass().getResourceAsStream("/surveyLastMatch.json"));
		final String label = "football-fixture-" + url;
		final QueryParams params = new QueryParams(Query.misc_listStorage);
		params.setSearch("storage.label='" + label + "'");
		final Result result = repository.list(params);
		final JsonNode fixture;
		if (result.size() > 0)
			fixture = new ObjectMapper().readTree(result.get(0).get("storage.storage").toString());
		else {
			fixture = WebClient
					.create("https://v3.football.api-sports.io/fixtures?" + url)
					.get()
					.header("x-rapidapi-key", token)
					.header("x-rapidapi-host", "v3.football.api-sports.io")
					.retrieve()
					.toEntity(JsonNode.class).block().getBody();
			final Storage storage = new Storage();
			storage.setLabel(label);
			storage.setStorage(new ObjectMapper().writeValueAsString(fixture));
			repository.save(storage);
		}
		return fixture.get("response").get(0);
	}
}
