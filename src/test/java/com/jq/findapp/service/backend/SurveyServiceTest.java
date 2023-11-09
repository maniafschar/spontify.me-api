package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.entity.ClientMarketing;
import com.jq.findapp.repository.Repository;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class SurveyServiceTest {
  @Autowired
  private SurveyService surveyService;

  @Autowired
  private Repository repository;

  @Test
  public void update() throws Exception {
    // given

    // when
    final SchedulerResult result = surveyService.update();
    final ClientMarketing clientMarketing = repository.one(ClientMarketing.class,
        new BigInteger(result.result.substring(result.result.lastIndexOf(" ") + 1)));

    // then
    assertNull(result.exception);
    assertEquals("Matchdays update: 7\nupdateLastMatchId: 5", result.result);
    assertNotNull(clientMarketing.getStorage());
  }

  @Test
  public void update_twice() throws Exception {
    // given
    surveyService.update();

    // when
    final SchedulerResult result = surveyService.update();

    // then
    assertNull(result.exception);
    assertEquals("Matchdays already run in last 24 hours", result.result);
  }
}
