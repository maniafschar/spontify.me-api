package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Location;
import com.jq.findapp.entity.Ticket;
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

@Service
public class LocationMarketingService {
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	public SchedulerResult sendEmails() {
		final SchedulerResult result = new SchedulerResult();
		try (final InputStream inHtml = getClass().getResourceAsStream("/template/email.html")) {
			final Client client = repository.one(Client.class, new BigInteger(4));
			String html = IOUtils.toString(inHtml, StandardCharsets.UTF_8)
					.replace("<jq:logo />", client.getUrl() + "/images/logo.png")
					.replace("<jq:pseudonym />", "")
					.replace("<jq:time />", Strings.formatDate(null, new Date(), contactTo.getTimezone()))
					.replace("<jq:text />", text.replaceAll("\n", "<br />"))
					.replace("<jq:link />", client.getUrl())
					.replace("<jq:url />", client.getUrl())
					.replace("<jq:newsTitle />", s2)
					.replace("<jq:image />", "");
			final JsonNode css = new ObjectMapper()
					.readTree(Attachment.resolve(client.getStorage()))
					.get("css");
			css.fieldNames().forEachRemaining(key -> html = html.replace("--" + key, css.get(key).asText()));
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch("location.skills like '%x.1%' and location.email like '%@%' and location.marketingMail not like '%x.1%'");
			params.setLimit(1);
			final Result list = repository.list(params);
			result.result = list.size() + " locations found\n";
			final String text = "Lieber Sky Sport Kunde,\n\n"
				+ "wie zufrieden bist Du mit der Auslastung Deiner Gastronomie zu Sport Events?\n\n"
				+ "Wir von fan-club.online möchten mehr gemeinsame Sporterlebnise auch in Deiner Gastronomie von und für Fans organisiern lassen. "
				+ "Bist Du an einer Kooperation interessiert? Sie ist für Dich kostenlos.\n\n"
				+ "Der Link zu unserer App: https://fan-club.online\n"
				+ "Bei Interesse antworte auf diese Email oder ruf uns an und wir besprechen alles telefonisch.\n\n"
				+ "Viele Grüße\n"
				+ "Mani Afschar Yazdi\n"
				+ "Geschäftsführer\n"
				+ "JNet Quality Consulting GmbH\n"
				+ "0172 6379434";
			for (int i = 0; i < list.size(); i++) {
				final Location location = repository.one(Location.class, (BigInteger) list.get(i).get("location.id"));
				notificationService.sendEmail(client, "", "mani.afschar@jq-consulting.de"/*location.getEmail()*/, "Sky Sport Events: Möchtest Du mehr Gäste? ", text, htmlText.toString());
				location.setMarketingMail((location.getMarketingMail() == null ? "" : location.getMarketingMail() + "|") + "x.1");
				repository.save(location);
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}
}
