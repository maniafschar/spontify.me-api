package com.jq.findapp.service.backend;

import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;

@Service
public class DbService {
	@Autowired
	private Repository repository;

	@Value("${app.server.webDir}")
	private String webDir;

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		try {
			repository.executeUpdate(
					"update Contact set age=cast((YEAR(current_timestamp) - YEAR(birthday) - case when MONTH(current_timestamp) < MONTH(birthday) or MONTH(current_timestamp) = MONTH(birthday) and DAY(current_timestamp) < DAY(birthday) then 1 else 0 end) as short) where birthday is not null");
			repository.executeUpdate(
					"update Contact set version=null where (version='0.9.9' or version='0.9.3') and os='android' and language='EN'");
			repository.executeUpdate(
					"update ContactNotification contactNotification set contactNotification.seen=true where contactNotification.seen=false and (select modifiedAt from Contact contact where contact.id=contactNotification.contactId)>contactNotification.createdAt and TIMESTAMPDIFF(MINUTE,contactNotification.createdAt,current_timestamp)>30");
			repository.executeUpdate(
					"update Contact set timezone='Europe/Berlin' where timezone is null");
			repository.executeUpdate(
					"update Log set webCall=substring(webCall, 1, instr(webCall, '(')-1) where instr(webCall, '(')>0");
			final LocalDate d = LocalDate.ofInstant(Instant.now().minus(Duration.ofDays(183)), ZoneId.systemDefault());
			repository.executeUpdate(
					"update ContactToken set token='' where modifiedAt is not null and modifiedAt<'" + d
							+ "' or modifiedAt is null and createdAt<'" + d + "'");
			final QueryParams params = new QueryParams(Query.misc_listClient);
			final Result list = repository.list(params);
			String clientUpdates = "";
			for (int i = 0; i < list.size(); i++) {
				final Client client = repository.one(Client.class, (BigInteger) list.get(i).get("client.id"));
				if (updateClient(client))
					clientUpdates += "|" + client.getId();
			}
			if (clientUpdates.length() > 0)
				result.result += (result.result.length() > 0 ? "\n" : "") + "ClientUpdate:"
						+ clientUpdates.substring(1);
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult backup() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/backup");
		try {
			new ProcessBuilder("./backup.sh").start().waitFor();
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}

	private boolean updateClient(final Client client) throws Exception {
		final String html = IOUtils.toString(new FileInputStream(webDir + client.getId() + "/index.html"),
				StandardCharsets.UTF_8);
		boolean modified = updateField("<meta name=\"email\" content=\"([^\"].*)\"", html, client.getEmail(),
				e -> client.setEmail(e));
		modified = updateField("<meta property=\\\"og:title\\\" content=\"([^\"].*)\"", html, client.getName(),
				e -> client.setName(e)) || modified;
		modified = updateField("<meta property=\\\"og:url\\\" content=\"([^\"].*)\"", html, client.getUrl(),
				e -> client.setUrl(e)) || modified;
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
			final JsonNode node = new ObjectMapper().readTree(Attachment.resolve(client.getStorage()));
			if (!!node.has("css") || !node.get("css").asText().equals(css)) {
				((ObjectNode) node).put("css", css);
				client.setStorage(new ObjectMapper().writeValueAsString(node));
				modified = true;
			}
		}
		if (modified) {
			repository.save(client);
			return true;
		}
		return false;
	}

	private boolean updateField(final String pattern, final String html, final String compare,
			final Consumer<String> set) {
		final Matcher matcher = Pattern.compile(pattern).matcher(html);
		if (matcher.find() && !matcher.group(1).equals(compare)) {
			set.accept(matcher.group(1));
			return true;
		}
		return false;
	}
}