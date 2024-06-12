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
		try {
			for (int i = 0; i < list.size(); i++) {
				params.setQuery(Query.contact_listNotificationId);
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
		try {
			String text = "Lieber Sky Sportsbar Kunde,\n\n" +
			"unsere Fußball Community ist auf der Suche nach den besten Sportbars, in denen sie Live-Übertragungen gemeinsam feiern können. " +
			"Ist Deine Bar eine coole Location für Fußball-Fans? Wenn Ja, würden wir Deine Bar in unserer App entsprechend kennzeichnen und Dir Aufkleber zusenden, die Du gerne prominent platzieren kannst, z.B. im Sanitärbereich oder in Deiner Ablage zum verteilen an die Gäste.\n\n" +
			"Wir freuen uns auf Dein Feedback\n" +
			"Viele Grüße\n" +
			"Mani Afschar\n";
		} catch (Exception ex) {
			result.exception = ex;
		}
		return result;
	}

	private void sendNotification(final Contact contact, final ClientMarketing clientMarketing) throws Exception {
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
		if (json.has("html")) {
			if (contact.getId().intValue() == 551)
				notificationService.sendNotification(null, contact,
						ContactNotificationTextType.clientMarketing,
						"m=" + clientMarketing.getId(), Strings.sanitize(json.get("html").asText(), 100));
		} else {
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
