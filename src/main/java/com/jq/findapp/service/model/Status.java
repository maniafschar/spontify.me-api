package com.jq.findapp.service.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Status {
	@JsonProperty("long")
	public String mylong;
	@JsonProperty("short")
	public String myshort;
	public Integer elapsed;
}