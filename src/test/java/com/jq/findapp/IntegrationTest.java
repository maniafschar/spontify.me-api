package com.jq.findapp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.safari.SafariDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class,
		TestConfig.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
				"server.port=9001", "server.servlet.context-path=/rest",
				"app.server.webSocket=http://127.0.0.1:9001/rest/" })
@ActiveProfiles("test")
public class IntegrationTest {
	private static String url = "http://localhost:9000/";

	@Autowired
	private Utils utils;

	@BeforeAll
	public static void start() throws Exception {
		new ProcessBuilder("./web.sh start".split(" ")).start();
		Util.driver = new SafariDriver();
		Util.driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		Util.driver.manage().window().setSize(new Dimension(450, 800));
	}

	@AfterAll
	public static void stop() throws Exception {
		Util.driver.close();
		new ProcessBuilder("./web.sh stop".split(" ")).start();
	}

	@Test
	public void run() throws Exception {
		try {
			init();
			register("testabcd", "test@jq-consulting.de");
			addLocation("location 1", "Melchiorstr. 9\n81479 München", false);
			addEvent();
			addRating();
			addLocation("location 1", "Melchiorstr. 9\n81479 München", true);
			addFriend();
		} catch (final Exception ex) {
			ex.printStackTrace();
			Util.sleep(10000);
			throw ex;
		}
	}

	private void init() throws Exception {
		utils.createClient();
		utils.createContact();
		utils.initPaypalSandbox();
		for (int i = 0; i < 20; i++) {
			Util.sleep(1000);
			Util.driver.get(url);
			try {
				Util.get("navigation item.events");
				break;
			} catch (final Exception ex) {
				// wait
			}
		}
	}

	private void register(final String pseudonym, final String email) {
		Util.click("navigation item.events");
		Util.click("hint button-text");
		Util.sendKeys("login input[name=\"email\"]", email);
		Util.click("login tab:nth-of-type(3)");
		Util.sendKeys("login input[name=\"pseudonym\"]", pseudonym);
		Util.click("login input-checkbox[name=\"agb\"]");
		Util.sleep(5000);
		Util.click("login button-text[onclick*=\"register\"]");
		Util.sleep(1000);
		final String s = Util.email(0).lines().reduce(
				(e, e2) -> e.startsWith("https://") ? e : e2.startsWith("https://") ? e2 : "").get();
		Util.driver.navigate().to(url + s.substring(s.indexOf('?')));
		Util.sendKeys("popup input[name=\"passwd\"]", "qwer1234");
		Util.click("popup button-text");
	}

	private void addLocation(final String name, final String address, final boolean duplicate) {
		Util.click("navigation item.events");
		Util.click("menu a[onclick*=\"pageEvent.edit\"]");
		Util.sleep(600);
		Util.click("popup button-text[onclick*=\"pageLocation.edit\"]");
		Util.sendKeys("popup input[name=\"name\"]", name);
		Util.get("popup textarea[name=\"address\"]").clear();
		Util.sendKeys("popup textarea[name=\"address\"]", address);
		Util.click("popup dialogButtons button-text");
		if (duplicate) {
			Util.get("popup popupHint").getText().toLowerCase().contains("location");
			Util.click("popupTitle");
		}
		Util.click("navigation item.home");
	}

	private void addEvent() {
		Util.click("navigation item.events");
		Util.click("menu a[onclick*=\"pageEvent.edit\"]");
		Util.sendKeys("popup input[name=\"location\"]", "loca");
		Util.sleep(1000);
		Util.click("popup eventLocationInputHelper ul li");
		if (!Util.get("popup .paid").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .paid should be invisible!");
		if (Util.get("popup .unpaid").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .unpaid should be visible!");
		Util.sendKeys("popup input[name=\"price\"]", "10");
		if (Util.get("popup .paid").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .paid should be visible!");
		if (!Util.get("popup .unpaid").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .unpaid should be invisible!");
		Util.get("popup input[name=\"price\"]").clear();
		Util.click("popup dialogButtons button-text[onclick*=\"save\"]");
		Util.get("popup input[name=\"startDate\"]").sendKeys("");
		new Actions(Util.driver).keyDown(Keys.SHIFT).sendKeys("\t\t\t\t\t\t").keyUp(Keys.SHIFT).build().perform();
		Util.get("popup input-hashtags").sendKeys("textabc");
		Util.sendKeys("popup textarea[name=\"description\"]", "mega sex");
		Util.click("popup dialogButtons button-text[onclick*=\"save\"]");
		Util.click("popup dialogButtons button-text[onclick*=\"save\"]");
		Util.click("navigation item.home");
	}

	private void addRating() throws Exception {
		Util.click("navigation item.events");
		Util.click("menu a[onclick*=\"ui.query.eventMy()\"]");
		final String id = Util.get("events list-row.participate").getAttribute("i").split("_")[0];
		utils.setEventDate(new BigInteger(id), new Timestamp(System.currentTimeMillis() - 86460000));
		Util.click("menu a[onclick*=\"ui.query.eventTickets()\"]");
		Util.click("events list-row.participate");
		Util.click("detail button-text[onclick*=\"ui.openRating\"]");
		Util.sleep(500);
		Util.get("detail button-text[onclick*=\"ui.openRating\"]").sendKeys("\t\t\t\t\t");
		new Actions(Util.driver).sendKeys(" ").build().perform();
		Util.click("navigation item.search");
		Util.click("search tabHeader tab[i=\"locations\"]");
		Util.click("search tabBody div.locations button-text[onclick*=\"pageSearch\"]");
		Util.click("search tabBody div.locations list-row:last-child");
		if (!Util.get("detail title").getText().contains("location 1"))
			throw new RuntimeException("New location not in list!");
		Util.get("detail input-rating[type=\"location\"][rating=\"80\"]");
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.click("search tabBody div.contacts button-text[onclick*=\"pageSearch\"]");
		Util.click("search tabBody div.contacts list-row:first-child");
		Util.click("detail input-rating[type=\"contact\"][rating=\"80\"]");
		Util.click("navigation item.events");
		Util.click("menu a[onclick*=\"ui.query.eventTickets()\"]");
		Util.click("events list-row.participate");
		Util.click("detail input-rating[type=\"event\"][rating=\"80\"]");
		Util.click("navigation item.home");
	}

	private void addFriend() {
		Util.click("navigation item.search");
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.get("search tabBody div.contacts input-checkbox").sendKeys("\t");
		Util.get("search tabBody div.contacts input-hashtags").sendKeys("pseudonym");
		Util.click("search tabBody div.contacts button-text[onclick*=\"pageSearch.\"]");
		Util.sleep(1500);
		Util.click("search tabBody div.contacts list-row:nth-of-type(1)");
		Util.click("detail button-text[name=\"buttonFriend\"]");
		Util.click("detail button-text[onclick*=\"sendRequestForFriendship\"]");
		Util.click("navigation item.search");
		Util.click("search tabBody div.contacts list-row:nth-of-type(1)");
		Util.click("detail button-text[name=\"buttonFriend\"]");
		Util.get("detail text[name=\"friend\"]>div>span");
		Util.click("navigation item.home");
	}

	static class Util {
		static WebDriver driver;

		private static String email(final int i) {
			sleep(1000);
			final List<String> files = Arrays.asList(new File("target/email").list());
			files.sort((e1, e2) -> e1.compareTo(e2));
			try {
				return IOUtils.toString(
						new FileInputStream(new File("target/email/" + files.get(files.size() - 1 - i))),
						StandardCharsets.UTF_8);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}

		static void sleep(final long ms) {
			try {
				Thread.sleep(ms);
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		static WebElement get(final String path) {
			final int maxWait = 20;
			int i = 0;
			while (driver.findElements(By.cssSelector("main [toggle]")).size() > 0 ||
					driver.findElements(By.cssSelector("main [class*=\"animated\"]")).size() > 0 ||
					driver.findElements(By.cssSelector("content[class*=\"SlideOut\"]")).size() > 0) {
				if (i++ > maxWait)
					throw new RuntimeException("Timeout during animation, tried to get " + path);
				sleep(100);
			}
			List<WebElement> list;
			i = 0;
			while ((list = driver.findElements(By.cssSelector(path))).size() == 0) {
				if (i++ > maxWait)
					throw new RuntimeException("Timeout during finding element " + path);
				sleep(100);
			}
			return list.get(0);
		}

		static void click(final String id) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click()", get(id));
		}

		static void sendKeys(final String path, final String keys) {
			for (int i = 0; i < 5; i++) {
				try {
					final WebElement e = get(path);
					e.clear();
					e.sendKeys(keys);
					return;
				} catch (final ElementNotInteractableException e) {
					sleep(250);
				}
			}
			throw new RuntimeException("Failed to send keys " + keys + " for path " + path);
		}
	}
}
