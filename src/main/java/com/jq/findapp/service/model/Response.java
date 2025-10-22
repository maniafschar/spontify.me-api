package com.jq.findapp.service.model;

import java.util.ArrayList;

public class Response {
	public String get;
	public Parameters parameters;
	public ArrayList<Error> errors;
	public int results;
	public Paging paging;
	public ArrayList<Match> response;
}