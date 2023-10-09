package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNews;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;

@Component
public class ContactNewsListener extends AbstractRepositoryListener<ContactNews> {
	@Override
	public void postPersist(final ContactNews contactNews) throws Exception {
		execute(contactNews);
	}

	@Override
	public void postUpdate(final ContactNews contactNews) throws Exception {
		execute(contactNews);
	}

	@Async
	public void execute(final ContactNews contactNews) {
		if (contactNews.getPublish() != null
				&& contactNews.getPublish().getTime() <= Instant.now().atZone(ZoneOffset.UTC).toEpochSecond() * 1000
				&& (contactNews.getNotified() == null || !contactNews.getNotified())) {
			try {
				contactNews.setNotified(true);
				repository.save(contactNews);
				final QueryParams params = new QueryParams(Query.contact_listId);
				params.setSearch("contact.clientId=" + contactNews.getClientId() + " and contact.verified=true");
				final Result users = repository.list(params);
				for (int i2 = 0; i2 < users.size(); i2++)
					notificationService.sendNotification(null,
							repository.one(Contact.class, (BigInteger) users.get(i2).get("contact.id")),
							ContactNotificationTextType.contactNews, "news=" + contactNews.getId(),
							contactNews.getDescription());
			} catch (final Exception e) {
				try {
					contactNews.setNotified(false);
					repository.save(contactNews);
				} catch (final Exception e1) {
				}
				throw new RuntimeException(e);
			}
		}
	}
}
