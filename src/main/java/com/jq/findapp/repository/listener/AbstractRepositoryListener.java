package com.jq.findapp.repository.listener;

import java.math.BigInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.NotificationService;

@Component
abstract class AbstractRepositoryListener {
	protected static Repository repository;
	protected static NotificationService notificationService;
	protected static BigInteger adminId;

	@Value("${app.admin.id}")
	private void setAdminId(BigInteger adminId) {
		AbstractRepositoryListener.adminId = adminId;
	}

	@Autowired
	private void setRepository(Repository repository) {
		AbstractRepositoryListener.repository = repository;
	}

	@Autowired
	private void setNotificationService(NotificationService notificationService) {
		AbstractRepositoryListener.notificationService = notificationService;
	}
}