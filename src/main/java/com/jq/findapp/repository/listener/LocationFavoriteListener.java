package com.jq.findapp.repository.listener;

import org.springframework.stereotype.Component;

import com.jq.findapp.entity.LocationFavorite;

@Component
public class LocationFavoriteListener extends AbstractRepositoryListener<LocationFavorite> {
	@Override
	public void prePersist(final LocationFavorite locationFavorite) {
		locationFavorite.setFavorite(Boolean.TRUE);
	}
}