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
		final Client client = repository.one(Client.class, new BigInteger(4));
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.location_listId);
		params.setSearch("location.skills like '%x.1%' and location.email like '%@%' and location.marketingMail not like '%x.1%'");
		params.setLimit(1);
		final Result list = repository.list(params);
		result.result = list.size() + " locations for update\n";
		final String html;
		try (final InputStream inHtml = getClass().getResourceAsStream("/template/email.html")) {
			html = new StringBuilder(IOUtils.toString(inHtml, StandardCharsets.UTF_8));
		}
		String text = "Lieber Sky Sport Kunde,\n\nwie zufrieden bist Du mit der Auslastung Deiner Gastronomie zu Sport Events?\n\nWir von fan-club.online möchten mehr gemeinsame Sporterlebnise auch in Deiner Gastronomie von und für Fans organisiern lassen. Bist Du an einer Kooperation interessiert? Sie ist für Dich kostenlos.\n\nViele Grüße\nMani Afschar Yazdi\nGeschäftsführer\nhttps://fan-club.online";
		for (int i = 0; i < list.size(); i++) {
			final Location location = repository.one(Location.class, (BigInteger) list.get(i).get("location.id"));
			final StringBuilder htmlText = new StringBuilder();
			Strings.replaceString(htmlText, "<jq:logo />", client.getUrl() + "/images/logo.png");
			Strings.replaceString(htmlText, "<jq:pseudonym />", "");
			Strings.replaceString(htmlText, "<jq:time />", Strings.formatDate(null, new Date(), contactTo.getTimezone()));
			Strings.replaceString(htmlText, "<jq:text />", text.replaceAll("\n", "<br />"));
			Strings.replaceString(htmlText, "<jq:link />", client.getUrl());
			Strings.replaceString(htmlText, "<jq:url />", client.getUrl());
			Strings.replaceString(htmlText, "<jq:newsTitle />", s2);
			Strings.replaceString(htmlText, "<jq:image />", "");
			final JsonNode css = new ObjectMapper()
					.readTree(Attachment.resolve(client.getStorage()))
					.get("css");
			css.fieldNames().forEachRemaining(key -> Strings.replaceString(htmlText, "--" + key, css.get(key).asText()));
			notificationService.sendEmail(client, "", "mani.afschar@jq-consulting.de"/*location.getEmail()*/, "Sky Sport Events: Möchtest Du mehr Gäste? ", text, htmlText.toString());
			location.setMarketingMail((location.getMarketingMail() == null ? "" : location.getMarketingMail() + "|") + "x.1");
			repository.save(location);
		}
		result.result = result.result + updated + " updated\n" + exceptions + " exceptions";
		return result;
	}
}
