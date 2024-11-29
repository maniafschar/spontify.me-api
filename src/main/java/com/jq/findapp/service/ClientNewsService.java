package com.jq.findapp.service;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.CronService.Job;
import com.jq.findapp.util.Text.TextId;

@Service
public class ClientNewsService {
	@Autowired
	private Repository repository;

	@Autowired
	private NotificationService notificationService;

	public void notify(final ClientNews clientNews) {
		if (clientNews.getPublish() != null
				&& (clientNews.getNotified() == null || !clientNews.getNotified())
				&& !Strings.isEmpty(clientNews.getSkills())
				&& !clientNews.getPublish().after(new Timestamp(Instant.now().toEpochMilli()))) {
			clientNews.setNotified(true);
			repository.save(clientNews);
			CompletableFuture.runAsync(() -> {
				final QueryParams params = new QueryParams(Query.contact_listId);
				params.setSearch(
						"contact.clientId=" + clientNews.getClientId()
								+ " and contact.verified=true and cast(REGEXP_LIKE(contact.skills, '"
								+ clientNews.getSkills() + "') as integer)=1");
				repository.list(params).forEach(e -> {
					notificationService.sendNotification(null,
							repository.one(Contact.class,
									(BigInteger) e.get("contact.id")),
							TextId.notification_clientNews, "news=" + clientNews.getId(),
							clientNews.getSource() + ": " + clientNews.getDescription());
				});
			});
		}
	}

	@Job
	public CronResult job() {
		final CronResult result = new CronResult();
		try {
			final QueryParams params = new QueryParams(Query.misc_listClient);
			final Result clients = repository.list(params);
			params.setUser(new Contact());
			params.setQuery(Query.misc_listNews);
			params.setSearch(
					"clientNews.notified=false and clientNews.publish<=cast('" + Instant.now() + "' as timestamp)");
			for (int i = 0; i < clients.size(); i++) {
				params.getUser().setClientId((BigInteger) clients.get(i).get("client.id"));
				final Result news = repository.list(params);
				for (int i2 = 0; i2 < news.size(); i2++)
					notify(repository.one(ClientNews.class, (BigInteger) news.get(i2).get("clientNews.id")));
			}
		} catch (final Exception e) {
			result.exception = e;
		}
		return result;
	}
}
