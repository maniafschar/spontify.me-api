package com.jq.findapp.entity;

import javax.persistence.Entity;

@Entity
public class GeoLocation extends BaseEntity {
	private Float latitude;
	private Float longitude;
	private String street;
	private String number;
	private String town;
	private String zipCode;
	private String country;

	public Float getLatitude() {
		return latitude;
	}

	public void setLatitude(Float latitude) {
		this.latitude = latitude;
	}

	public Float getLongitude() {
		return longitude;
	}

	public void setLongitude(Float longitude) {
		this.longitude = longitude;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public String getNumber() {
		return number;
	}

	public void setNumber(String number) {
		this.number = number;
	}

	public String getTown() {
		return town;
	}

	public void setTown(String town) {
		this.town = town;
	}

	public String getZipCode() {
		return zipCode;
	}

	public void setZipCode(String zipCode) {
		this.zipCode = zipCode;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getFormatted() {
		String s = "";
		if (street != null)
			s += street + (number == null ? "" : " " + number);
		if (zipCode == null) {
			if (town != null)
				s += "\n" + town;
		} else {
			s += "\n" + zipCode;
			if (town != null)
				s += " " + town;
		}
		return s;
	}
}