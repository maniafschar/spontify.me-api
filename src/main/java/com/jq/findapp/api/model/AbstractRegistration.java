package com.jq.findapp.api.model;

import java.math.BigInteger;

import com.jq.findapp.entity.Contact.Device;
import com.jq.findapp.entity.Contact.OS;

public abstract class AbstractRegistration {
	private OS os;
	private Device device;
	private String language;
	private String version;
	private String timezone;
	private BigInteger clientId;

	public OS getOs() {
		return os;
	}

	public void setOs(OS os) {
		this.os = os;
	}

	public Device getDevice() {
		return device;
	}

	public void setDevice(Device device) {
		this.device = device;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(BigInteger clientId) {
		this.clientId = clientId;
	}
}