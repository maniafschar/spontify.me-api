package com.jq.findapp.api.model;

import java.sql.Date;

public class InternalRegistration extends AbstractRegistration {
	private boolean agb;
	private Date birthday;
	private int time;
	private Short gender;
	private String email;
	private String pseudonym;

	public boolean isAgb() {
		return agb;
	}

	public void setAgb(boolean agb) {
		this.agb = agb;
	}

	public Date getBirthday() {
		return birthday;
	}

	public void setBirthday(Date birthday) {
		this.birthday = birthday;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public Short getGender() {
		return gender;
	}

	public void setGender(Short gender) {
		this.gender = gender;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPseudonym() {
		return pseudonym;
	}

	public void setPseudonym(String pseudonym) {
		this.pseudonym = pseudonym;
	}
}