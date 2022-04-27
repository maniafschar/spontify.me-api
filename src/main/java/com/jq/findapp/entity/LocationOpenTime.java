package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Time;

import javax.persistence.Entity;

@Entity
public class LocationOpenTime extends BaseEntity {
	private BigInteger locationId;
	private Short day;
	private Time openAt;
	private Time closeAt;

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(BigInteger locationId) {
		this.locationId = locationId;
	}

	public Short getDay() {
		return day;
	}

	public void setDay(Short day) {
		this.day = day;
	}

	public Time getOpenAt() {
		return openAt;
	}

	public void setOpenAt(Time openAt) {
		this.openAt = openAt;
	}

	public Time getCloseAt() {
		return closeAt;
	}

	public void setCloseAt(Time closeAt) {
		this.closeAt = closeAt;
	}
}