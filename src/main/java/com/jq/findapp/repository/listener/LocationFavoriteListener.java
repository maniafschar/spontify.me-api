package com.jq.findapp.repository.listener;

import javax.persistence.PrePersist;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.jq.findapp.entity.LocationFavorite;

public class LocationFavoriteListener extends AbstractRepositoryListener {
	@PrePersist
	public void prePersist(LocationFavorite locationFavorite)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		locationFavorite.setFavorite(Boolean.TRUE);
	}
}