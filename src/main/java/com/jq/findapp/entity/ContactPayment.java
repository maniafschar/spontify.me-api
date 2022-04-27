package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;

import javax.persistence.Entity;

@Entity
public class ContactPayment extends BaseEntity {
	private BigInteger contactId;
	private BigInteger locationId;
	private String txNo;
	private String tax;
	private Long paid;
	private Long days;
	private Date paidUntil;

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

	public String getTxNo() {
		return txNo;
	}

	public void setTxNo(String txNo) {
		this.txNo = txNo;
	}

	public String getTax() {
		return tax;
	}

	public void setTax(String tax) {
		this.tax = tax;
	}

	public Long getPaid() {
		return paid;
	}

	public void setPaid(Long paid) {
		this.paid = paid;
	}

	public Long getDays() {
		return days;
	}

	public void setDays(Long days) {
		this.days = days;
	}

	public Date getPaidUntil() {
		return paidUntil;
	}

	public void setPaidUntil(Date paidUntil) {
		this.paidUntil = paidUntil;
	}
}