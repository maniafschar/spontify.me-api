package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ClientNews;
import com.jq.findapp.service.backend.ClientNewsService;

@Component
public class ClientNewsListener extends AbstractRepositoryListener<ClientNews> {
	@Autowired
	private ClientNewsService clientNewsService;

	@Override
	public void postPersist(final ClientNews clientNews) {
		clientNewsService.notify(clientNews);
	}

	@Override
	public void postUpdate(final ClientNews clientNews) {
		clientNewsService.notify(clientNews);
	}
}