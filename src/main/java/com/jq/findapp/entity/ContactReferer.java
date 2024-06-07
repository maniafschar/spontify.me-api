package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class ContactReferer extends BaseEntity {
	private BigInteger contactId;
	private String footprint;
	private String ip;

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public String getFootprint() {
		return footprint;
	}

	public void setFootprint(String footprint) {
		this.footprint = footprint;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}
}