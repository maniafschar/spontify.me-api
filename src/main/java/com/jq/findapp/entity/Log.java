package com.jq.findapp.entity;

import java.math.BigInteger;

import javax.persistence.Entity;

@Entity
public class Log extends BaseEntity {
	private String method;
	private String body;
	private String query;
	private String uri;
	private BigInteger contactId;
	private int time;
	private int status;
	private int port;

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public BigInteger getContactId() {
		return contactId;
	}

	public void setContactId(BigInteger contactId) {
		this.contactId = contactId;
	}

	public int getTime() {
		return time;
	}

	public void setTime(int time) {
		this.time = time;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}
}
