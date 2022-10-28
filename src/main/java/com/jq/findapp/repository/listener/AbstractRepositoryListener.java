package com.jq.findapp.repository.listener;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;

public abstract class AbstractRepositoryListener<T extends BaseEntity> {
	@Autowired
	protected Repository repository;

	@Autowired
	protected NotificationService notificationService;

	@Value("${app.admin.id}")
	protected BigInteger adminId;

	public void prePersist(T entity) throws Exception {
	}

	public void postPersist(T entity) throws Exception {
	}

	public void preUpdate(T entity) throws Exception {
	}

	public void postUpdate(T entity) throws Exception {
	}

	public void preRemove(T entity) throws Exception {
	}

	public void postRemove(T entity) throws Exception {
	}
}