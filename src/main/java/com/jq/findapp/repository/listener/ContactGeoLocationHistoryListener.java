package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.ContactGeoLocationHistory;

@Component
public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener<ContactGeoLocationHistory> {
	@Override
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {
	}
}