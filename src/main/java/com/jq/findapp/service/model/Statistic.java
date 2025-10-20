package com.jq.findapp.service.model;

import java.util.ArrayList;

import com.jq.findapp.service.model.Test.Team;

public class Statistic {
	public Team team;
	public ArrayList<Statistic> statistics;
	public String type;
	public Object value;
	public Games games;
	public int offsides;
	public Shots shots;
	public Goals goals;
	public Passes passes;
	public Tackles tackles;
	public Duels duels;
	public Dribbles dribbles;
	public Fouls fouls;
	public Cards cards;
	public Penalty penalty;
}