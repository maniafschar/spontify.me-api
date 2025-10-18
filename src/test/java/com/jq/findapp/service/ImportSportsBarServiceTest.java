package com.jq.findapp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

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
import com.jq.findapp.service.CronService.CronResult;
import com.jq.findapp.service.ImportSportsBarService.Results;
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

	public void zipCode() throws Exception {
		// given
		this.utils.createContact(BigInteger.ONE);
		final Storage storage = new Storage();
		storage.setLabel("importSportBars");
		storage.setStorage("[]");
		this.repository.save(storage);

		// when
		final Results result = this.importSportsBarService.zipCode("80331");

		// then
		assertTrue(result.processed > 10, "" + result.processed);
		assertTrue(result.imported > 2);
	}

	@Test
	public void cronImport() throws Exception {
		// given
		new File("dazn/52.73-7.75.json").delete();
		new File("dazn/52.73-7.75.json.processed").delete();
		Files.copy(this.getClass().getResourceAsStream("/json/52.73-7.75.json"), Path.of("dazn/52.73-7.75.json"));

		// when
		final CronResult result = this.importSportsBarService.cronImport();

		// then
		assertNull(result.exception);
		assertEquals("5 imports", result.body);
	}
}