package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EntityListeners;

import com.jq.findapp.repository.listener.LocationVisitListener;

@Entity
@EntityListeners(LocationVisitListener.class)
public class LocationVisit extends BaseEntity {
	private BigInteger contactId;
	private BigInteger locationId;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public BigInteger getLocationId() {
		return locationId;
	}

	public void setLocationId(BigInteger locationId) {
		this.locationId = locationId;
	}

}
