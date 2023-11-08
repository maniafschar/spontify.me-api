package com.jq.findapp.service.backend;

import org.junit.jupiter.api.Test;

import spinjar.com.fasterxml.jackson.databind.DeserializationFeature;
import spinjar.com.fasterxml.jackson.databind.JsonNode;
import spinjar.com.fasterxml.jackson.databind.ObjectMapper;

public class JsonTest {
  @Test
  public void test() throws Exception
  {
    JsonNode matchDay =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readTree(JsonTest.class.getResourceAsStream("/test.json"));
    System.out.println(matchDay.get("fixture")
        .get("referee")
        .asText());
    for (int i = 0; i < matchDay.get("lineups")
        .size(); i++)
      for (int i2 = 0; i2 < matchDay.get("lineups")
          .get(i)
          .get("startXI")
          .size(); i2++)
        System.out.println(matchDay.get("lineups")
            .get(i)
            .get("startXI")
            .get(i2)
            .get("player")
            .get("name")
            .asText());
    for (int i = 0; i < matchDay.get("players")
        .size(); i++)
      for (int i2 = 0; i2 < matchDay.get("players")
          .get(i)
          .get("players")
          .size(); i2++)
        System.out.println(matchDay.get("players")
            .get(i)
            .get("players")
            .get(i2)
            .get("player")
            .get("name")
            .asText() + " "
            + matchDay.get("players")
                .get(i)
                .get("players")
                .get(i2)
                .get("statistics")
                .get(0)
                .get("games")
                .get("rating")
                .asText()
            + " " + matchDay.get("players")
                .get(i)
                .get("players")
                .get(i2)
                .get("statistics")
                .get(0)
                .get("games")
                .get("minutes")
                .asText());
  }
}
