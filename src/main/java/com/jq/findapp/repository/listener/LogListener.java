package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.Log;

@Component
public class LogListener extends AbstractRepositoryListener<Log> {
	@Override
	public void prePersist(final Log log) {
		if (log.getBody() != null) {
			log.setBody(log.getBody().trim());
			if (log.getBody().length() > 255)
				log.setBody(log.getBody().substring(0, 255));
			else if (log.getBody().length() == 0)
				log.setBody(null);
		}
	}

	@Override
	public void preUpdate(Log entity) throws Exception {
		prePersist(entity);
	}
}