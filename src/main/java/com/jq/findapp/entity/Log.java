package com.jq.findapp.entity;

import java.math.BigInteger;

import jakarta.persistence.Entity;

@Entity
public class Log extends BaseEntity {
	private String method;
	private String body;
	private String webCall;
	private String referer;
	private String query;
	private String ip;
	private String uri;
	private BigInteger clientId;
	private BigInteger contactId;
	private LogStatus status;
	private int port;
	private int time;

	public enum LogStatus {
		Error, Exception, Offline, Ok, Redirection, Running, Unauthorized;

		public static LogStatus get(int httpCode) {
			return httpCode < 300 ? Ok : httpCode < 400 ? Redirection : httpCode < 500 ? Exception : Error;
		}
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(final String method) {
		this.method = method;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(final String query) {
		this.query = query;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(final String uri) {
		this.uri = uri;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(final BigInteger contactId) {
		this.contactId = contactId;
	}

	public int getTime() {
		return time;
	}

	public void setTime(final int time) {
		this.time = time;
	}

	public LogStatus getStatus() {
		return status;
	}

	public void setStatus(final LogStatus status) {
		this.status = status;
	}

	public int getPort() {
		return port;
	}

	public void setPort(final int port) {
		this.port = port;
	}

	public String getBody() {
		return body;
	}

	public void setBody(final String body) {
		this.body = body;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(final String ip) {
		this.ip = ip;
	}

	public String getReferer() {
		return referer;
	}

	public void setReferer(final String referer) {
		this.referer = referer;
	}

	public String getWebCall() {
		return webCall;
	}

	public void setWebCall(final String webCall) {
		this.webCall = webCall;
	}

	public BigInteger getClientId() {
		return clientId;
	}

	public void setClientId(final BigInteger clientId) {
		this.clientId = clientId;
	}
}