package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ContactGeoLocationHistory extends BaseEntity {
	private BigInteger contactId;
	private BigInteger geoLocationId;
	private Float altitude;
	private Float heading;
	private Float speed;
	private Float accuracy;
	private Boolean manual;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getGeoLocationId() {
		return geoLocationId;
	}

	public void setGeoLocationId(BigInteger geoLocationId) {
		this.geoLocationId = geoLocationId;
	}

	public Float getAltitude() {
		return altitude;
	}

	public void setAltitude(Float altitude) {
		this.altitude = altitude;
	}

	public Float getHeading() {
		return heading;
	}

	public void setHeading(Float heading) {
		this.heading = heading;
	}

	public Float getSpeed() {
		return speed;
	}

	public void setSpeed(Float speed) {
		this.speed = speed;
	}

	public Float getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(Float accuracy) {
		this.accuracy = accuracy;
	}

	public Boolean getManual() {
		return manual;
	}

	public void setManual(Boolean manual) {
		this.manual = manual;
	}
}