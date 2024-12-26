package com.jq.findapp.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Group;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

@Service
public class DbService {
	@Autowired
	private ClientNewsService clientNewsService;

	@Autowired
	private Repository repository;

	@Value("${app.server.webDir}")
	private String webDir;

	@Value("${spring.datasource.username}")
	private String user;

	@Value("${spring.datasource.password}")
	private String password;

	@Cron("0 4")
	public CronResult cron() {
		final CronResult result = new CronResult();
		try {
			repository.executeUpdate(
					"update Contact set age=cast((YEAR(current_timestamp) - YEAR(birthday) - case when MONTH(current_timestamp) < MONTH(birthday) or MONTH(current_timestamp) = MONTH(birthday) and DAY(current_timestamp) < DAY(birthday) then 1 else 0 end) as short) where birthday is not null");
			repository.executeUpdate(
					"update Contact set timezone='" + Strings.TIME_OFFSET + "' where timezone is null");
			final String d = Instant.now().minus(Duration.ofDays(183)).toString().substring(0, 19);
			repository.executeUpdate(
					"update ContactToken set token='' where modifiedAt is not null and modifiedAt<cast('" + d
							+ "' as timestamp) or modifiedAt is null and createdAt<cast('" + d + "' as timestamp)");
			repository.executeUpdate(
					"update ContactNotification contactNotification set contactNotification.seen=true where contactNotification.seen=false and contactNotification.createdAt<cast('"
							+ Instant.now().minus(Duration.ofHours(12)).toString().substring(0, 19)
							+ "' as timestamp) and contactNotification.createdAt>cast('"
							+ Instant.now().minus(Duration.ofDays(7)).toString().substring(0, 19)
							+ "' as timestamp) and (select modifiedAt from Contact contact where contact.id=contactNotification.contactId)>contactNotification.createdAt");
			final QueryParams params = new QueryParams(Query.misc_listClient);
			final Result list = repository.list(params);
			String clientUpdates = "";
			for (int i = 0; i < list.size(); i++) {
				final Client client = repository.one(Client.class, (BigInteger) list.get(i).get("client.id"));
				if (updateClient(client))
					clientUpdates += "|" + client.getId();
			}
			if (clientUpdates.length() > 0)
				result.body += (result.body.length() > 0 ? "\n" : "") + "ClientUpdate:"
						+ clientUpdates.substring(1);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Cron("30 0")
	public CronResult cronCleanUp() {
		final CronResult result = new CronResult();
		try {
			result.body = repository.cleanUpAttachments();
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	@Cron(group = Group.Five)
	public CronResult cronBackup() {
		final CronResult result = new CronResult();
		try {
			String tables = "block chat client client_marketing client_marketing_result client_news contact contact_bluetooth contact_chat contact_geo_location_history contact_group contact_group_link contact_link contact_marketing contact_notification contact_referer contact_token contact_video_call contact_visit event event_participate event_rating geo_location ip location location_favorite location_visit storage";
			if (LocalDateTime.now().getHour() == 23 && LocalDateTime.now().getMinute() > 50)
				tables += " log ticket";
			new ProcessBuilder("./backup.sh", user, password, tables).start().waitFor();
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private boolean updateClient(final Client client) throws Exception {
		final File file = new File(webDir + client.getId() + "/index.html");
		if (!file.exists())
			return false;
		final String html = IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8);
		updateField("<meta name=\"email\" content=\"([^\"].*)\"", html, e -> client.setEmail(e));
		updateField("<meta property=\\\"og:title\\\" content=\"([^\"].*)\"", html, e -> client.setName(e));
		updateField("<meta property=\\\"og:url\\\" content=\"([^\"].*)\"", html, e -> client.setUrl(e));
		final ObjectNode node = (ObjectNode) Json.toNode(Attachment.resolve(client.getStorage()));
		if (!node.has("lang"))
			node.set("lang", Json.createObject());
		final List<String> langs = Arrays.asList("DE", "EN");
		for (final String lang : langs) {
			final JsonNode json = Json.toNode(IOUtils.toString(
					new FileInputStream(webDir + client.getId() + "/js/lang/" + lang + ".json"),
					StandardCharsets.UTF_8));
			if (!node.get("lang").has(lang))
				((ObjectNode) node.get("lang")).set(lang, Json.createObject());
			if (!node.get("lang").get(lang).has("buddy") ||
					!node.get("lang").get(lang).get("buddy").asText()
							.equals(json.get("labels").get("buddies").asText())) {
				((ObjectNode) node.get("lang").get(lang)).set("buddy", json.get("labels").get("buddy"));
				((ObjectNode) node.get("lang").get(lang)).set("buddies", json.get("labels").get("buddies"));
			}
		}
		String css = IOUtils.toString(new FileInputStream(webDir + client.getId() + "/css/main.css"),
				StandardCharsets.UTF_8);
		Matcher matcher = Pattern.compile(":root \\{([^}])*").matcher(css);
		if (matcher.find()) {
			css = matcher.group();
			matcher = Pattern.compile("--([^:].*): (.*);").matcher(css);
			css = "{";
			while (matcher.find())
				css += "\"" + matcher.group(1) + "\":\"" + matcher.group(2) + "\",";
			css = css.substring(0, css.length() - 1) + "}";
			if (!node.has("css") || !Json.toString(node.get("css")).equals(css))
				node.set("css", Json.toNode(css));
		}
		client.setStorage(Json.toString(node));
		if (client.modified()) {
			repository.save(client);
			return true;
		}
		return false;
	}

	private void updateField(final String pattern, final String html,
			final Consumer<String> set) {
		final Matcher matcher = Pattern.compile(pattern).matcher(html);
		if (matcher.find())
			set.accept(matcher.group(1));
	}

	@Cron("30 4")
	public CronResult cronStatistics() throws Exception {
		final CronResult result = new CronResult();
		final BigInteger clientId = BigInteger.valueOf(4l);
		final Map<String, Object> data = new HashMap<>();
		final QueryParams params = new QueryParams(Query.misc_statsUser);
		params.setUser(new Contact());
		params.getUser().setClientId(clientId);
		params.setLimit(0);
		data.put("user", repository.list(params).getList());
		params.setQuery(Query.misc_statsLog);
		data.put("log", repository.list(params).getList());
		params.setQuery(Query.misc_statsApi);
		data.put("api", repository.list(params).getList());
		params.setQuery(Query.misc_statsLocations);
		params.setLimit(15);
		data.put("locations", repository.list(params).getList());
		data.put("update", Instant.now().toString());
		IOUtils.write(Json.toString(data),
				new FileOutputStream("statistics" + clientId + ".json"), StandardCharsets.UTF_8);
		return result;
	}
}
