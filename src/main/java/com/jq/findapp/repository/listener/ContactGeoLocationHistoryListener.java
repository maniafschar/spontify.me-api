package com.jq.findapp.repository.listener;

import javax.persistence.PostPersist;

import com.jq.findapp.entity.ContactGeoLocationHistory;

public class ContactGeoLocationHistoryListener extends AbstractRepositoryListener {
	@PostPersist
	public void postPersist(final ContactGeoLocationHistory contactGeoLocationHistory) throws Exception {

	}
}