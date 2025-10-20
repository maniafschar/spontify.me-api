package com.jq.findapp.service.model;

import java.util.ArrayList;

import com.jq.findapp.service.model.Test.Team;

public class Lineup {
	public Team team;
	public String formation;
	public ArrayList<StartXI> startXI;
	public ArrayList<Substitute> substitutes;
	public Coach coach;
}