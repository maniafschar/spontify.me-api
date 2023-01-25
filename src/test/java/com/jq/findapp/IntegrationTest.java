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
import org.openqa.selenium.ElementNotInteractableException;
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
		addLocation("location 1", "Melchiorstr. 9\n81479 München", false);
		addLocation("location 1", "Melchiorstr. 9\n81479 München", true);
		addFriend();
	}

	private void init() throws Exception {
		utils.createContact();
		driver.get(url);
	}

	private void register(String pseudonym, String email) {
		Util.click("home homeHeader");
		Util.click("hint buttontext");
		Util.sendKeys("login input[name=\"email\"]", email);
		Util.click("login tab:nth-of-type(3)");
		Util.sendKeys("login input[name=\"pseudonym\"]", pseudonym);
		Util.click("login input[name=\"agb\"]");
		Util.sleep(5000);
		Util.click("login buttontext[onclick*=\"register\"]");
		final String s = Util.email(0).lines().reduce(
				(e, e2) -> e.startsWith("https://") ? e : e2.startsWith("https://") ? e2 : "").get();
		driver.navigate().to(url + s.substring(s.indexOf('?')));
		Util.sendKeys("popup input[name=\"passwd\"]", "qwer1234");
		Util.click("popup buttontext");
	}

	private void addLocation(String name, String address, boolean duplicate) {
		Util.click("navigation item.events");
		Util.click("menu a[onclick*=\"pageEvent.edit\"]");
		Util.sleep(500);
		Util.click("buttontext[onclick*=\"pageLocation.edit\"]");
		Util.sendKeys("popup input[name=\"name\"]", name);
		Util.get("popup textarea[name=\"address\"]").clear();
		Util.sendKeys("popup textarea[name=\"address\"]", address);
		Util.click("popup dialogButtons buttontext");
		if (duplicate) {
			Util.get("popup popupHint").getText().toLowerCase().contains("location");
			Util.click("popupTitle");
		}
		Util.click("navigation item.home");
		Util.sleep(300);
	}

	private void addFriend() {
		Util.click("navigation item.search");
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.sendKeys("search div.contacts input[name=\"keywords\"]", "pseudonym");
		Util.click("search div.contacts buttontext[onclick*=\"pageSearch.\"]");
		Util.click("search div.contacts row:nth-of-type(1)");
		Util.click("detail buttontext[name=\"buttonFriend\"]");
		Util.click("detail buttontext[onclick*=\"sendRequestForFriendship\"]");
	}

	private static class Util {
		private static String email(int i) {
			sleep(500);
			final List<String> files = Arrays.asList(new File("target/email").list());
			files.sort((e1, e2) -> e1.compareTo(e2));
			try {
				return IOUtils.toString(
						new FileInputStream(new File("target/email/" + files.get(files.size() - 1 - i))),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private static void sleep(long ms) {
			try {
				Thread.sleep(ms);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		private static WebElement get(String id) {
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

		private static void click(String id) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click()", get(id));
		}

		private static void sendKeys(String id, String keys) {
			for (int i = 0; i < 5; i++) {
				try {
					get(id).sendKeys(keys);
					return;
				} catch (NoSuchElementException | ElementNotInteractableException e) {
					sleep(500);
				}
			}
			throw new RuntimeException("Failed to send keys " + keys + " for id " + id);
		}
	}
}
