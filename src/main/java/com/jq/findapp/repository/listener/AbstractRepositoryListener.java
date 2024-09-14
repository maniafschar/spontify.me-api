package com.jq.findapp.repository.listener;

import org.springframework.beans.factory.annotation.Autowired;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;

public abstract class AbstractRepositoryListener<T extends BaseEntity> {
	@Autowired
	protected Repository repository;

	@Autowired
	protected NotificationService notificationService;

	public void prePersist(final T entity) throws IllegalArgumentException {
	}

	public void postPersist(final T entity) throws IllegalArgumentException {
	}

	public void preUpdate(final T entity) throws IllegalArgumentException {
	}

	public void postUpdate(final T entity) throws IllegalArgumentException {
	}

	public void preRemove(final T entity) throws IllegalArgumentException {
	}

	public void postRemove(final T entity) throws IllegalArgumentException {
	}
}