package com.jq.findapp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.safari.SafariDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.util.Utils;

@ExtendWith({ SpringExtension.class })
@SpringBootTest(classes = { FindappApplication.class,
		JpaTestConfiguration.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@TestPropertySource(properties = "server.port=9001")
@ActiveProfiles("test")
public class IntegrationTest {
	private static WebDriver driver;
	private static String url = "http://localhost:9000/";

	@Autowired
	private Utils utils;

	@BeforeAll
	public static void start() throws Exception {
		new ProcessBuilder("./web.sh start".split(" ")).start();
		driver = new SafariDriver();
	}

	@AfterAll
	public static void stop() throws Exception {
		driver.close();
		new ProcessBuilder("./web.sh stop".split(" ")).start();
	}

	@Test
	public void run() throws Exception {
		init();
		register("testabcd", "test@jq-consulting.de");
		addLocation("location 1", "Melchiorstr. 9\n81479 München");
		addFriend();
	}

	private void init() throws Exception {
		utils.createContact();
		driver.get(url);
	}

	private void register(String pseudonym, String email) {
		click("home homeBody buttontext:nth-of-type(1)");
		click("hint buttontext");
		get("login input[name=\"email\"]").sendKeys(email);
		click("login tab:nth-of-type(3)");
		get("login input[name=\"pseudonym\"]").sendKeys(pseudonym);
		click("login input[name=\"agb\"]");
		sleep(5000);
		click("login buttontext:nth-of-type(1)");
		String s = email().lines().reduce(
				(e, e2) -> e.startsWith("https://") ? e : e2.startsWith("https://") ? e2 : "").get();
		driver.navigate().to(url + s.substring(s.indexOf('?')));
		get("popup input[name=\"passwd\"]").sendKeys("qwer1234");
		click("popup buttontext");
	}

	private void addLocation(String name, String address) {
		click("home homeBody buttontext:nth-of-type(2)");
		click("menu a[onclick*=\"pageLocation.edit\"]");
		get("popup input[name=\"name\"]").sendKeys(name);
		get("popup textarea[name=\"address\"]").sendKeys(address);
		click("popup input[name=\"locationbudget\"]:nth-of-type(2)");
		click("popup input[name=\"parkingOption2\"]:nth-of-type(2)");
		click("popup input[name=\"locationcategory\"]:nth-of-type(5)");
		click("popup buttontext");
		click("main>buttonIcon[class*=\"center\"]");
	}

	private void addFriend() {
		click("home buttonIcon[onclick*=\"search\"]");
		get("search input[name=\"searchKeywords\"]").sendKeys("pseudonym");
		click("search buttontext[onclick*=\"saveSearch\"]");
		click("search row:nth-of-type(1)");
		click("main>buttonIcon[onclick*=\"toggleFavorite\"]");
		click("detail text[name=\"block\"] buttontext:nth-of-type(1)");
	}

	private String email() {
		sleep(500);
		final List<String> files = Arrays.asList(new File("target/email").list());
		files.sort((e1, e2) -> e1.compareTo(e2));
		try {
			return IOUtils.toString(new FileInputStream(new File("target/email/" + files.get(files.size() - 1))),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e2) {
			throw new RuntimeException(e2);
		}
	}

	private WebElement get(String id) {
		for (int i = 0; i < 20; i++) {
			try {
				sleep(500);
				return driver.findElement(By.cssSelector(id));
			} catch (NoSuchElementException e) {
				// wait
			}
		}
		throw new RuntimeException(id + " not found");
	}

	private void click(String id) {
		((JavascriptExecutor) driver).executeScript("arguments[0].click()", get(id));
	}
}
