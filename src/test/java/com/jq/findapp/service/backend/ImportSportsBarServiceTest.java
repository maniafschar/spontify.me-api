package com.jq.findapp.service.backend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.entity.Storage;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.backend.ImportSportsBarService.Results;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class ImportSportsBarServiceTest {
	@Autowired
	private ImportSportsBarService importSportsBarService;

	@Autowired
	private Repository repository;

	@Autowired
	private Utils utils;

	@Test
	public void importSportsBars() throws Exception {
		// given
		utils.createContact(BigInteger.ONE);
		final Storage storage = new Storage();
		storage.setLabel("importSportBars");
		storage.setStorage("[]");
		repository.save(storage);

		// when
		final Results result = importSportsBarService.runZipCode("80331");

		// then
		assertTrue(result.processed == 20);
		assertTrue(result.imported > 2);
	}
}