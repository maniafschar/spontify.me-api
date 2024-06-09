package com.jq.findapp.repository.listener;

import java.math.BigInteger;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ClientMarketingResult;
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
	public void postRemove(final ClientMarketing clientMarketing) throws Exception {
		final QueryParams params = new QueryParams(Query.misc_listMarketingResult);
		params.setSearch("clientMarketingResult.clientMarketingId=" + clientMarketing.getId());
		final Result result = repository.list(params);
		if (result.size() > 0)
			repository.delete(repository.one(ClientMarketingResult.class,
					(BigInteger) result.get(0).get("clientMarketingResult.id")));
	}
}
