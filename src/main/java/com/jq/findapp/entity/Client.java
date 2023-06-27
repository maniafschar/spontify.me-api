package com.jq.findapp.entity;

import jakarta.persistence.Entity;

@Entity
public class Client extends BaseEntity {
	private Boolean club;
	private String css;
	private String name;
	private String email;
	private String url;

	public String getName() {
		return name;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(final String url) {
		this.url = url;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(final String email) {
		this.email = email;
	}

	public String getCss() {
		return css;
	}

	public void setCss(final String css) {
		this.css = css;
	}

	public Boolean getClub() {
		return club;
	}

	public void setClub(final Boolean club) {
		this.club = club;
	}
}