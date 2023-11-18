package com.jq.findapp.repository.listener;

import java.math.BigInteger;
import java.time.Instant;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketingResult;
import com.jq.findapp.entity.Contact;
import com.jq.findapp.entity.ClientMarketing.ClientMarketingMode;
import com.jq.findapp.entity.ContactNotification.ContactNotificationTextType;
import com.jq.findapp.repository.Query;
import com.jq.findapp.repository.Query.Result;
import com.jq.findapp.repository.QueryParams;

@Component
public class ClientMarketingListener extends AbstractRepositoryListener<ClientMarketing> {
	@Override
	public void postPersist(final ClientMarketing clientMarketing) throws Exception {
		final ClientMarketingResult clientMarketingResult = new ClientMarketingResult();
		clientMarketingResult.setClientMarketingId(clientMarketing.getId());
		clientMarketingResult.setPublished(false);
		repository.save(clientMarketingResult);
	}

	@Override
	public void postUpdate(final ClientMarketing clientMarketing) throws Exception {
		if (clientMarketing.getMode() == ClientMarketingMode.Live
				&& clientMarketing.getStartDate().getTime() > Instant.now().toEpochMilli()
				&& clientMarketing.getImage() != null)
			execute(clientMarketing);
	}

	@Override
	public void postRemove(final ClientMarketing clientMarketing) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
		final Result result = repository.list(params);
		if (result.size() > 0)
			repository.delete(repository.one(ClientMarketingResult.class,
					(BigInteger) result.get(0).get("clientMarketingResult.id")));
	}

	@Async
	private void execute(final ClientMarketing clientMarketing) {
		try {
			final QueryParams params = new QueryParams(Query.contact_listId);
			params.setSearch("contact.clientId=" + clientMarketing.getClientId() + " and contact.verified=true");
			final Result users = repository.list(params);
			for (int i2 = 0; i2 < users.size(); i2++)
				notificationService.sendNotification(null,
						repository.one(Contact.class, (BigInteger) users.get(i2).get("contact.id")),
						ContactNotificationTextType.clientMarketing, "m=" + clientMarketing.getId());
		} catch (final Exception e) {
			throw new RuntimeException(e);
		}
	}
}