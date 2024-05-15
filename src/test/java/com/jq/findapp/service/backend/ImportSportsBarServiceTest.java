package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
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
		final int count = importSportsBarService.importZip("80331");

		// then
		assertEquals(20, count);
	}
}