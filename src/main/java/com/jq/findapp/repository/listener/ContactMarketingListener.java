package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.entity.ContactMarketing;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.backend.MarketingService;

@Component
public class ContactMarketingListener extends AbstractRepositoryListener<ContactMarketing> {
	@Autowired
	private MarketingService marketingService;

	@Override
	public void prePersist(final ContactMarketing contactMarketing) throws Exception {
		preUpdate(contactMarketing);
	}

	@Override
	public void preUpdate(final ContactMarketing contactMarketing) throws Exception {
		if (contactMarketing.getStorage() != null && contactMarketing.getStorage().length() > 2) {
			final ObjectMapper om = new ObjectMapper();
			final JsonNode json = om.readTree(Attachment.resolve(contactMarketing.getStorage()));
			json.fieldNames().forEachRemaining(key -> {
				if (json.get(key).has("t")) {
					final ArrayNode a = (ArrayNode) json.get(key).get("a");
					try {
						final JsonNode poll = om.readTree(Attachment.resolve(repository
								.one(ClientMarketing.class, contactMarketing.getClientMarketingId()).getStorage()));
						final JsonNode question = poll.get("questions").get(Integer.valueOf(key.substring(1)));
						if (question.has("answers")) {
							final int value = question.get("answers").size() - 1;
							if (a.size() > 0 && (!question.has("multiple") || !question.get("multiple").asBoolean()))
								a.set(0, value);
							else if (a.size() == 0 || a.get(a.size() - 1).intValue() != value)
								a.add(value);
						}
					} catch (final JsonProcessingException e) {
						throw new RuntimeException(e);
					}
				}
			});
		}
	}

	@Override
	public void postPersist(final ContactMarketing contactMarketing) throws Exception {
		postUpdate(contactMarketing);
	}

	@Override
	public void postUpdate(final ContactMarketing contactMarketing) throws Exception {
		if (contactMarketing.getFinished())
			marketingService.synchronizeResult(contactMarketing.getClientMarketingId());
	}

}
