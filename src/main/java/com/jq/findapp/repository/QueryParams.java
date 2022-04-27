package com.jq.findapp.repository;

import java.math.BigInteger;

import com.jq.findapp.entity.Contact;

public class QueryParams {
	private Query query;
	private String search;
	private String searchGeoLocation;
	private Double longitude;
	private Double latitude;
	private Integer distance;
	private int limit = 100;
	private Contact user;
	private boolean sort = true;
	private BigInteger id;

	public QueryParams(final Query query) {
		this.query = query;
	}

	public Query getQuery() {
		return query;
	}

	public void setQuery(Query query) {
		this.query = query;
	}

	public BigInteger getId() {
		return id;
	}

	public void setId(BigInteger id) {
		this.id = id;
	}

	public String getSearch() {
		return search;
	}

	public void setSearch(String search) {
		this.search = search;
	}

	public Double getLongitude() {
		return longitude;
	}

	public void setLongitude(Double longitude) {
		this.longitude = longitude;
	}

	public Double getLatitude() {
		return latitude;
	}

	public void setLatitude(Double latitude) {
		this.latitude = latitude;
	}

	public Integer getDistance() {
		return distance;
	}

	public void setDistance(Integer distance) {
		this.distance = distance;
	}

	public Contact getUser() {
		return user;
	}

	public void setUser(Contact user) {
		this.user = user;
	}

	String getSearchGeoLocation() {
		return searchGeoLocation;
	}

	void setSearchGeoLocation(String searchGeoLocation) {
		this.searchGeoLocation = searchGeoLocation;
	}

	public boolean isSort() {
		return sort;
	}

	public void setSort(boolean sort) {
		this.sort = sort;
	}

	public int getLimit() {
		return limit;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	@Override
	public String toString() {
		return query
				+ (longitude == null ? "" : ", longitude: " + longitude + ", latitude: " + latitude)
				+ (", limit: " + limit)
				+ (distance == null ? "" : ", distance: " + distance)
				+ (user == null ? "" : ", userid: " + user.getId())
				+ (search == null ? "" : ", search: " + search);
	}
}
