package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.jq.findapp.entity.LocationFavorite;

@Component
public class LocationFavoriteListener extends AbstractRepositoryListener<LocationFavorite> {
	@Override
	public void prePersist(final LocationFavorite locationFavorite)
			throws JsonMappingException, JsonProcessingException, IllegalAccessException {
		locationFavorite.setFavorite(Boolean.TRUE);
	}
}