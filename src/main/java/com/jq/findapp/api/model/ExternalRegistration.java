package com.jq.findapp.api.model;

import java.util.Map;

import com.jq.findapp.service.AuthenticationExternalService.From;

public class ExternalRegistration extends AbstractRegistration {
	private Map<String, String> user;
	private From from;
	private String publicKey;

	public Map<String, String> getUser() {
		return user;
	}

	public void setUser(Map<String, String> user) {
		this.user = user;
	}

	public From getFrom() {
		return from;
	}

	public void setFrom(From from) {
		this.from = from;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	@Override
	public String toString() {
		return super.toString() +
				"\nfrom: " + getFrom() +
				"\nuser: " + getUser();
	}
}