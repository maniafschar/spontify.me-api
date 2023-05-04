package com.jq.findapp.entity;

import java.math.BigInteger;
import java.sql.Date;

import com.jq.findapp.entity.Contact.ContactType;
import com.jq.findapp.repository.Repository;

import jakarta.persistence.Entity;
import jakarta.persistence.Transient;

@Entity
public class ClientMarketing extends BaseEntity {
	private BigInteger clientId;
	private Date startDate;
	private Date endDate;
	private String age;
	private String gender;
	private String language;
	private String region;
	private String storage;

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(BigInteger clientId) {
		this.clientId = clientId;
	}

	public Date getStartDate() {
		return startDate;
	}

	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public String getAge() {
		return age;
	}

	public void setAge(String age) {
		this.age = age;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getStorage() {
		return storage;
	}

	public void setStorage(String storage) {
		this.storage = storage;
	}

	@Transient
	@Override
	public boolean writeAccess(BigInteger user, Repository repository) {
		return repository.one(Contact.class, user).getType() == ContactType.adminContent;
	}
}