package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;

@Component
public class ClientNewsListener extends AbstractRepositoryListener<ClientNews> {
	@Override
	public void postPersist(final ClientNews clientNews) throws Exception {
		this.execute(clientNews);
	}

	@Override
	public void postUpdate(final ClientNews clientNews) throws Exception {
		this.execute(clientNews);
	}

	@Async
	public void execute(final ClientNews clientNews) {
		if (clientNews.getPublish() != null
				&& clientNews.getPublish().getTime() <= Instant.now().atZone(ZoneOffset.UTC).toEpochSecond() * 1000
				&& (clientNews.getNotified() == null || !clientNews.getNotified())) {
			try {
				clientNews.setNotified(true);
				this.repository.save(clientNews);
				final QueryParams params = new QueryParams(Query.contact_listId);
				params.setSearch("contact.clientId=" + clientNews.getClientId() + " and contact.verified=true");
				final Result users = this.repository.list(params);
				for (int i2 = 0; i2 < users.size(); i2++) {
					final Contact contact = this.repository.one(Contact.class,
							(BigInteger) users.get(i2).get("contact.id"));
					if (clientNews.getCategoryId() == null)
						this.notificationService.sendNotification(null,
								contact,
								ContactNotificationTextType.clientNews, "news=" + clientNews.getId(),
								clientNews.getDescription());
				}
			} catch (final Exception e) {
				try {
					clientNews.setNotified(false);
					this.repository.save(clientNews);
				} catch (final Exception e1) {
				}
				throw new RuntimeException(e);
			}
		}
	}
}
