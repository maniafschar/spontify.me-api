package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.entity.GeoLocation;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;
import com.jq.findapp.util.Text;
import com.jq.findapp.util.Text.TextId;

@Service
public class MarketingService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Text text;

	public SchedulerResult notification() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
		final String today = Instant.now().toString().substring(0, 19);
		params.setSearch("clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
		final Result list = repository.list(params);
		params.setQuery(Query.contact_listNotificationId);
		try {
			for (int i = 0; i < list.size(); i++) {
				params.setSearch("m=" + list.get(i).get("clientMarketing.id"));
				params.getUser().setClientId((BigInteger) list.get(i).get("clientMarketing.clientId"));
				final Result contacts = repository.list(params);
				result.result += list.get(i).get("clientMarketing.id") + ": " + contacts.size() + "\n";
				for (int i2 = 0; i2 < contacts.size(); i2++)
					sendNotification(repository.one(Contact.class, (BigInteger) contacts.get(i2).get("contact.id")),
							repository.one(ClientMarketing.class, (BigInteger) list.get(i).get("clientMarketing.id")));
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	public SchedulerResult notificationSportbars() {
		final SchedulerResult result = new SchedulerResult();
		final QueryParams params = new QueryParams(Query.misc_listMarketing);
		params.setUser(new Contact());
		final String today = Instant.now().toString().substring(0, 19);
		params.setSearch("clientMarketing.startDate<=cast('" + today
				+ "' as timestamp) and clientMarketing.endDate>=cast('" + today + "' as timestamp)");
		final Result list = repository.list(params);
		params.setQuery(Query.location_list);
		final String text = "Lieber Sky Sportsbar Kunde,\n\n"
				+ "unsere Fußball Community ist auf der Suche nach den besten Locations, in denen sie Live-Übertragungen gemeinsam feiern können. "
				+ "Ist Deine Bar eine coole Location für Fußball-Fans?\n\nNäere Infos findest Du unter Deinen persönlichen Link:\n\n"
				+ "https://fan-club.online/?m={clientMarketing.id}&i={location.id}&h={location.hash}"
				+ "\n\nWir freuen uns auf Dein Feedback\n"
				+ "Viele Grüße\n"
				+ "Mani Afschar Yazdi\n"
				+ "Geschäftsführer\n"
				+ "JNet Quality Consulting GmbH\n"
				+ "0172 6379434";
		try {
			for (int i = 0; i < list.size(); i++) {
				if (((String) list.get(i).get("clientMarketing.storage")).contains("Sky Kunde")) {
					final Client client = repository.one(Client.class, (BigInteger) list.get(i).get("clientMarketing.clientId"));
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
					params.setSearch("location.email like '%@%' and location.skills like '%x.1%' and cast(REGEXP_LIKE(marketingMail,'" + "') as integer)=0");
					final Result locations = repository.list(params);
					for (int i2 = 0; i2 < locations.size(); i2++) {
						final Location location = repository.one(Location.class, (BigInteger) locations.get(i2).get("location.id"));
						if (location.getSecret() == null) {
							location.setSecret(Strings.generatePin(64));
							repository.save(location);
						}
						String s = text.replace("{clientMarketing.id}", "" + list.get(i).get("clientMarketing.id"))
								.replace("{location.id}", "" + location.getId())
								.replace("{location.hash}", "" + location.getSecret().hashCode());
						notificationService.sendEmail(client, "", "mani.afschar@jq-consulting.de"/* location.getEmail() */,
								"Sky Sport Events: möchtest Du mehr Gäste?", s, html.replace("<jq:text />",
										s.replace("\n", "<br />").replace(client.getUrl(),
												"<a href=\"" + client.getUrl() + "?marketing=sky&client=" + location.getId()
														+ "\">" + client.getUrl() + "</a>")));
						location.setMarketingMail(
								(Strings.isEmpty(location.getMarketingMail()) ? "" : location.getMarketingMail() + "|")
										+ list.get(i).get("clientMarketing.id"));
						repository.save(location);
					}
				}
			}
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private void sendNotification(final Contact contact, final ClientMarketing clientMarketing) throws Exception {
		if ("0.6.7".compareTo(contact.getVersion()) > 0)
			return;

		if (!Strings.isEmpty(clientMarketing.getLanguage())
				&& !clientMarketing.getLanguage().equals(contact.getLanguage()))
			return;

		if (!Strings.isEmpty(clientMarketing.getGender())
				&& !clientMarketing.getGender().contains("" + contact.getGender()))
			return;

		if (!Strings.isEmpty(clientMarketing.getAge())
				&& (contact.getAge() == null
						|| Integer.valueOf(clientMarketing.getAge().split(",")[0]) > contact.getAge()
						|| Integer.valueOf(clientMarketing.getAge().split(",")[1]) < contact.getAge()))
			return;

		if (!Strings.isEmpty(clientMarketing.getRegion())) {
			final QueryParams params = new QueryParams(Query.contact_listGeoLocationHistory);
			params.setSearch("contactGeoLocationHistory.contactId=" + contact.getId());
			final Result result = repository.list(params);
			if (result.size() > 0) {
				final GeoLocation geoLocation = repository.one(GeoLocation.class,
						(BigInteger) result.get(0).get("contactGeoLocationHistory.geoLocationId"));
				final String region = " " + clientMarketing.getRegion() + " ";
				if (!region.contains(" " + geoLocation.getCountry() + " ") &&
						!region.toLowerCase().contains(" " + geoLocation.getTown().toLowerCase() + " ")) {
					String s = geoLocation.getZipCode();
					boolean zip = false;
					while (s.length() > 1 && !zip) {
						zip = region.contains(" " + geoLocation.getCountry() + "-" + s + " ");
						s = s.substring(0, s.length() - 1);
					}
					if (!zip)
						return;
				}
			} else
				return;
		}

		final JsonNode json = new ObjectMapper().readTree(Attachment.resolve(clientMarketing.getStorage()));
		if (json.has("html"))
			notificationService.sendNotification(null, contact,
					ContactNotificationTextType.clientMarketing,
					"m=" + clientMarketing.getId(), Strings.sanitize(json.get("html").asText(), 100));
		else {
			final TextId textId = TextId
					.valueOf("marketing_p" + json.get("type").asText().substring(1));
			notificationService.sendNotification(null, contact,
					ContactNotificationTextType.clientMarketingPoll,
					"m=" + clientMarketing.getId(),
					text.getText(contact, textId).replace("{0}", json.get("homeName").asText() +
							" : " + json.get("awayName").asText()));
		}
	}

}
