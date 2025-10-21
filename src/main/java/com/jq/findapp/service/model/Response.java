package com.jq.findapp.service.model;

import java.util.List;

public class Response {
	public String get;
	public Parameters parameters;
	public List<Error> errors;
	public int results;
	public Paging paging;
	public List<Match> response;
}