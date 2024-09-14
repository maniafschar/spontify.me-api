package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.QueryParams;
import com.jq.findapp.util.Text.TextId;

@Component
public class ClientNewsListener extends AbstractRepositoryListener<ClientNews> {
	@Override
	public void postPersist(final ClientNews clientNews) {
		this.execute(clientNews);
	}

	@Override
	public void postUpdate(final ClientNews clientNews) {
		this.execute(clientNews);
	}

	@Async
	public void execute(ClientNews clientNews) {
		if (clientNews.getPublish() != null
				&& clientNews.getPublish().getTime() <= Instant.now().atZone(ZoneOffset.UTC).toEpochSecond() * 1000
				&& (clientNews.getNotified() == null || !clientNews.getNotified())) {
			try {
				final ClientNews clientNews2 = repository.one(ClientNews.class, clientNews.getId());
				if (clientNews2 != null) {
					clientNews2.setNotified(true);
					this.repository.save(clientNews2);
					if (clientNews2.getCategory() != null) {
						final QueryParams params = new QueryParams(Query.contact_listId);
						params.setSearch(
								"contact.clientId=" + clientNews2.getClientId() + " and contact.verified=true");
						final String cat = "|" + clientNews2.getCategory() + "|";
						repository.list(params).forEach(e -> {
							final Contact contact = this.repository.one(Contact.class,
									(BigInteger) e.get("contact.id"));
							if (("|" + contact.getSkills() + "|").contains(cat))
								this.notificationService.sendNotificationSync(null,
										contact,
										TextId.notification_clientNews, "news=" + clientNews2.getId(),
										clientNews2.getSource() + ": " + clientNews2.getDescription());
						});
					}
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
