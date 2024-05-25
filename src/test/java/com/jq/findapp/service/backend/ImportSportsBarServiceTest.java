package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.service.backend.ImportSportsBarService.Results;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ImportSportsBarServiceTest {
	@Autowired
	private ImportSportsBarService importSportsBarService;

	@Autowired
	private Utils utils;

	@Test
	public void importSportsBars() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);

		// when
		final Results result = importSportsBarService.importZip("80331");

		// then
		assertTrue(result.processed == 20);
		assertTrue(result.imported > 2);
	}

	@Test
	public void annotation() {
		Supplier<SchedulerResult> s = importSportsBarService::importSportsBars;
		System.out.println(s.toString());
	}
}