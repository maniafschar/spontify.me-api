package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.repository.Repository;

@Service
public class SurveyService {
	@Autowired
	private Repository repository;

	@Value("${app.sports.api.token}")
	private String token;

	public SchedulerResult sync() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/sync");
		syncMatchdays();
		return result;
	}

	private void syncMatchdays() {
		final BigInteger clientId = BigInteger.valueOf(4);
		final MatchDay[] matchDays = WebClient
				.create("https://v3.football.api-sports.io/fixtures?team=157&season="
						+ Instant.now().atZone(ZoneId.systemDefault()).getYear())
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(MatchDay[].class).block().getBody();
		if (matchDays != null) {
			for (int i = 0; i < matchDays.length; i++) {
				final ClientMarketing clientMarketing = new ClientMarketing();
				clientMarketing.setStartDate(new Timestamp(matchDays[i].fixture.date.getTime() + (2 * 60 * 60 * 1000)));
				clientMarketing
						.setEndDate(new Timestamp(clientMarketing.getStartDate().getTime() + (24 * 60 * 60 * 1000)));
				clientMarketing.setClientId(clientId);
				clientMarketing.setStorage("" + matchDays[i].fixture.id);
			}
		}
	}

	private void syncMatch(final int fixtureId) {
		final BigInteger clientId = BigInteger.valueOf(4);
		final MatchDay matchDay = WebClient
				.create("https://v3.football.api-sports.io/fixtures?id=" + fixtureId)
				.get()
				.header("x-rapidapi-key", token)
				.header("x-rapidapi-host", "v3.football.api-sports.io")
				.retrieve()
				.toEntity(MatchDay.class).block().getBody();
		if (matchDay != null) {

		}
	}
}

class MatchDay {
	public Fixture fixture;
	public League league;
	public Teams teams;
	public Goals goals;
	public Score score;
	public ArrayList<Event> events;
	public ArrayList<Lineup> lineups;
	public ArrayList<Statistic> statistics;
	public ArrayList<Player> players;
}

class Assist {
	public int id;
	public String name;
}

class Away {
	public int id;
	public String name;
	public String logo;
	public boolean winner;
}

class Cards {
	public int yellow;
	public int red;
}

class Coach {
	public int id;
	public String name;
	public String photo;
}

class Colors {
	public Player player;
	public Goalkeeper goalkeeper;
}

class Dribbles {
	public int attempts;
	public int success;
	public int past;
}

class Duels {
	public int total;
	public int won;
}

class Event {
	public Time time;
	public Team team;
	public Player player;
	public Assist assist;
	public String type;
	public String detail;
	public String comments;
}

class Extratime {
	public Object home;
	public Object away;
}

class Fixture {
	public int id;
	public String referee;
	public String timezone;
	public Date date;
	public int timestamp;
	public Periods periods;
	public Venue venue;
	public Status status;
}

class Fouls {
	public int drawn;
	public int committed;
}

class Fulltime {
	public int home;
	public int away;
}

class Games {
	public int minutes;
	public int number;
	public String position;
	public String rating;
	public boolean captain;
	public boolean substitute;
}

class Goalkeeper {
	public String primary;
	public String number;
	public String border;
}

class Goals {
	public int home;
	public int away;
	public int total;
	public int conceded;
	public int assists;
	public int saves;
}

class Halftime {
	public int home;
	public int away;
}

class Home {
	public int id;
	public String name;
	public String logo;
	public boolean winner;
}

class League {
	public int id;
	public String name;
	public String country;
	public String logo;
	public String flag;
	public int season;
	public String round;
}

class Lineup {
	public Team team;
	public Coach coach;
	public String formation;
	public ArrayList<StartXI> startXI;
	public ArrayList<Substitute> substitutes;
}

class Passes {
	public int total;
	public int key;
	public String accuracy;
}

class Penalty {
	public Object home;
	public Object away;
	public Object won;
	public Object commited;
	public int scored;
	public int missed;
	public int saved;
}

class Periods {
	public int first;
	public int second;
}

class Player {
	public int id;
	public String name;
	public String primary;
	public String number;
	public String border;
	public String pos;
	public String grid;
	public String photo;
}

class Player5 {
	public Team team;
	public ArrayList<Player> players;
	public Player player;
	public ArrayList<Statistic> statistics;
}

class Score {
	public Halftime halftime;
	public Fulltime fulltime;
	public Extratime extratime;
	public Penalty penalty;
}

class Shots {
	public int total;
	public int on;
}

class StartXI {
	public Player player;
}

class Statistic {
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

class Status {
	@JsonProperty("long")
	public String mylong;
	@JsonProperty("short")
	public String myshort;
	public int elapsed;
}

class Substitute {
	public Player player;
}

class Tackles {
	public int total;
	public int blocks;
	public int interceptions;
}

class Team {
	public int id;
	public String name;
	public String logo;
	public Colors colors;
	public Date update;
}

class Teams {
	public Home home;
	public Away away;
}

class Time {
	public int elapsed;
	public int extra;
}

class Venue {
	public int id;
	public String name;
	public String city;
}
