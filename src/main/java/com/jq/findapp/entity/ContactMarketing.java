package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;

@Entity
public class ContactMarketing extends BaseEntity {
	private BigInteger contactId;
	private String data;
	@Enumerated(EnumType.STRING)
	private ContactMarketingType type;

	public enum ContactMarketingType {
		CollectFriends
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public ContactMarketingType getType() {
		return type;
	}

	public void setType(ContactMarketingType type) {
		this.type = type;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}
}