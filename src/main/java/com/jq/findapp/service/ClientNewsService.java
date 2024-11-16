package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Text.TextId;

@Service
public class ClientNewsService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	@Async
	public void notify(ClientNews clientNews) {
		if (clientNews.getPublish() != null
				&& !clientNews.getPublish().after(new Timestamp(Instant.now().toEpochMilli()))
				&& (clientNews.getNotified() == null || !clientNews.getNotified())
				&& !Strings.isEmpty(clientNews.getSkills())) {
			try {
				clientNews.setNotified(true);
				repository.save(clientNews);
				final QueryParams params = new QueryParams(Query.contact_listId);
				params.setSearch(
						"contact.clientId=" + clientNews.getClientId()
								+ " and contact.verified=true and cast(REGEXP_LIKE(contact.skills, '"
								+ clientNews.getSkills() + "') as integer)=1");
				repository.list(params).forEach(e -> {
					notificationService.sendNotificationSync(null,
							repository.one(Contact.class,
									(BigInteger) e.get("contact.id")),
							TextId.notification_clientNews, "news=" + clientNews.getId(),
							clientNews.getSource() + ": " + clientNews.getDescription());
				});
			} catch (final Exception e) {
				try {
					clientNews.setNotified(false);
					repository.save(clientNews);
				} catch (final Exception e1) {
				}
				throw new RuntimeException(e);
			}
		}
	}

}
