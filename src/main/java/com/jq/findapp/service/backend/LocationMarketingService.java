package com.jq.findapp.service.backend;

import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Iterator;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.Client;
import com.jq.findapp.entity.Location;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
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
			final Client client = repository.one(Client.class, new BigInteger("4"));
			final QueryParams params = new QueryParams(Query.location_listId);
			params.setSearch(
					"location.skills like '%x.1%' and location.email like '%@%' and location.marketingMail not like '%x.1%'");
			params.setLimit(1);
			final Result list = repository.list(params);
			result.result = list.size() + " locations found\n";
			final String text = "Lieber Sky Sport Kunde,\n\n"
					+ "wie zufrieden bist Du mit der Auslastung Deiner Gastronomie zu Sport Events?\n\n"
					+ "Wir, ein innovativer Fußball-App Hersteller aus München, möchten mehr gemeinsame Sporterlebnise auch in Deiner Gastronomie von und für Fans organisiern lassen. "
					+ "Bist Du an einer Kooperation interessiert? Sie ist für Dich kostenlos.\n\n"
					+ "Der Link zu unserer App: " + client.getUrl() + "\n"
					+ "Bei Interesse antworte auf diese Email oder ruf uns an und wir besprechen alles telefonisch.\n\n"
					+ "Viele Grüße\n"
					+ "Mani Afschar Yazdi\n"
					+ "Geschäftsführer\n"
					+ "JNet Quality Consulting GmbH\n"
					+ "0172 6379434";
			String html = IOUtils.toString(inHtml, StandardCharsets.UTF_8)
					.replace("<jq:logo />", client.getUrl() + "/images/logo.png")
					.replace("<jq:pseudonym />", "")
					.replace("<jq:time />", Strings.formatDate(null, new Date(), "Europe/Berlin"))
					.replace("<jq:link />", "")
					.replace("<jq:url />", "")
					.replace("<jq:newsTitle />", "")
					.replace("<jq:image />", "");
			final int a = html.indexOf("</a>");
			html = html.substring(0, a + 4) + html.substring(html.indexOf("<jq:text"));
			html = html.substring(0, html.lastIndexOf("<div>", a)) + html.substring(a);
			final JsonNode css = new ObjectMapper()
					.readTree(Attachment.resolve(client.getStorage()))
					.get("css");
			final Iterator<String> it = css.fieldNames();
			while (it.hasNext()) {
				final String key = it.next();
				html = html.replace("--" + key, css.get(key).asText());
			}
			for (int i = 0; i < list.size(); i++) {
				final Location location = repository.one(Location.class, (BigInteger) list.get(i).get("location.id"));
				notificationService.sendEmail(client, "", "mani.afschar@jq-consulting.de"/* location.getEmail() */,
						"Sky Sport Events: möchtest Du mehr Gäste? ", text, html.replace("<jq:text />",
								text.replace("\n", "<br />").replace(client.getUrl(),
										"<a href=\"" + client.getUrl() + "?marketing=sky&client=" + location.getId()
												+ "\">" + client.getUrl() + "</a>")));
				location.setMarketingMail(
						(Strings.isEmpty(location.getMarketingMail()) ? "" : location.getMarketingMail() + "|")
								+ "x.1");
				repository.save(location);
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}
}
