package com.jq.findapp.selenium;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.ElementNotInteractableException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.interactions.Actions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.FindappApplication;
import com.jq.findapp.TestConfig;
import com.jq.findapp.util.Utils;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class,
		TestConfig.class }, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
				"server.port=9001", "server.servlet.context-path=/rest",
				"app.server.webSocket=http://127.0.0.1:9001/rest/" })
@ActiveProfiles("test")
public class AppTest {
	private static String url = "http://localhost:9000/";

	@Autowired
	private Utils utils;

	@BeforeEach
	public void start() throws Exception {
		new ProcessBuilder("./web.sh", "start").start();
		Util.sleep(5000);
		Util.driver = new ChromeDriver();
		Util.driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		Util.driver.manage().window().setSize(new Dimension(450, 800));
		this.init();
	}

	@AfterEach
	public void stop() throws Exception {
		Util.driver.close();
		new ProcessBuilder("./web.sh", "stop").start();
	}

	@Test
	public void run() throws Exception {
		try {
			this.register("testabcd", "test@jq-consulting.de");
			this.addLocation("location 1", "Melchiorstr. 9\n81479 München", false);
			this.addEvent();
			this.addRating();
			this.addLocation("location 1", "Melchiorstr. 9\n81479 München", true);
			this.addFriend();
		} catch (final Exception ex) {
			ex.printStackTrace();
			Util.sleep(600000000);
		}
	}

	private void init() throws Exception {
		this.utils.createContact(BigInteger.valueOf(8));
		this.utils.initPaypalSandbox();
		Util.sleep(3000);
		for (int i = 0; i < 20; i++) {
			Util.sleep(1000);
			Util.driver.get(url);
			try {
				Util.get("dialog-navigation item.events");
				break;
			} catch (final Exception ex) {
				// wait
			}
		}
	}

	private void register(final String pseudonym, final String email) {
		Util.click("dialog-navigation item.events");
		Util.click("dialog-hint button-text");
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
		new Actions(Util.driver).sendKeys("qwer1234").build().perform();
		Util.sendKeys("dialog-popup input[name=\"passwd\"]", "qwer1234");
		Util.click("dialog-popup button-text");
	}

	private void addLocation(final String name, final String address, final boolean duplicate) {
		Util.click("dialog-navigation item.events");
		Util.click("dialog-menu a[onclick*=\"pageEvent.edit\"]");
		Util.sleep(600);
		Util.click("dialog-popup button-text[onclick*=\"pageLocation.edit\"]");
		Util.sendKeys("dialog-popup input[name=\"name\"]", name);
		Util.get("dialog-popup textarea[name=\"address\"]").clear();
		Util.sendKeys("dialog-popup textarea[name=\"address\"]", address);
		Util.click("dialog-popup dialogButtons button-text[onclick*=\"pageLocation.save\"]");
		if (duplicate) {
			if (!Util.get("dialog-popup popupHint").getText().toLowerCase().contains("location"))
				throw new RuntimeException("Expected duplicate location!");
			Util.click("dialog-popup popupTitle");
		}
		Util.click("dialog-navigation item.home");
	}

	private void addEvent() {
		Util.click("dialog-navigation item.events");
		Util.click("dialog-menu a[onclick*=\"pageEvent.edit\"]");
		if (!Util.get("dialog-popup .picture").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .picture should be invisible!");
		if (!Util.get("dialog-popup .url").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .url should be invisible!");
		Util.sendKeys("dialog-popup input[name=\"price\"]", "10");
		new Actions(Util.driver).sendKeys("\n").build().perform();
		if (Util.get("dialog-popup .picture").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .picture should be visible!");
		if (Util.get("dialog-popup .url").getAttribute("style").contains("none"))
			throw new RuntimeException("Event .url should be visible!");
		Util.get("dialog-popup input[name=\"price\"]").clear();
		Util.click("dialog-popup dialogButtons button-text[onclick*=\"selectLocation\"]");
		Util.sendKeys("dialog-popup input[name=\"location\"]", "loca");
		Util.sleep(1000);
		Util.click("dialog-popup eventLocationInputHelper ul li");
		Util.sleep(500);
		Util.click("dialog-popup dialogButtons button-text[onclick*=\"save\"]");
		Util.sendKeys("dialog-popup input-date[name=\"startDate\"]", "");
		Util.sendKeys("dialog-popup input-hashtags textarea", "textabc");
		Util.sendKeys("dialog-popup textarea[name=\"description\"]", "mega sex");
		Util.click("dialog-popup dialogButtons button-text[onclick*=\"save\"]");
		Util.click("dialog-popup dialogButtons button-text[onclick*=\"save\"]");
		Util.click("dialog-navigation item.home");
	}

	private void addRating() throws Exception {
		Util.click("dialog-navigation item.events");
		Util.click("dialog-menu a[onclick*=\"ui.query.eventMy()\"]");
		final String id = Util.get("events list-row.participate").getAttribute("i").split("_")[0];
		this.utils.setEventDate(new BigInteger(id), new Timestamp(System.currentTimeMillis() - 86460000));
		Util.click("dialog-menu a[onclick*=\"ui.query.eventTickets()\"]");
		Util.click("events list-row.participate");
		Util.click("detail button-text[onclick*=\"ui.openRating\"]");
		Util.click("dialog-popup input-rating button-text");
		Util.click("dialog-navigation item.search");
		Util.click("search tabHeader tab[i=\"locations\"]");
		Util.click("search tabBody div.locations button-text[onclick*=\"pageSearch\"]");
		Util.click("search tabBody div.locations list-row:first-child");
		if (!Util.get("detail title").getText().contains("location 1"))
			throw new RuntimeException("New location not in list!");
		Util.get("detail input-rating[type=\"location\"][rating=\"80\"]");
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.click("search tabBody div.contacts button-text[onclick*=\"pageSearch\"]");
		Util.click("search tabBody div.contacts list-row:first-child");
		Util.click("detail input-rating[type=\"contact\"][rating=\"80\"]");
		Util.click("dialog-navigation item.events");
		Util.click("dialog-menu a[onclick*=\"ui.query.eventTickets()\"]");
		Util.click("events list-row.participate");
		Util.click("detail input-rating[type=\"event\"][rating=\"80\"]");
		Util.click("dialog-navigation item.home");
	}

	private void addFriend() {
		Util.click("dialog-navigation item.search");
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.get("search tabBody div.contacts input-checkbox").sendKeys("\t");
		Util.get("search tabBody div.contacts input-hashtags").sendKeys("pseudonym");
		Util.click("search tabBody div.contacts button-text[onclick*=\"pageSearch.\"]");
		Util.sleep(1500);
		Util.click("search tabBody div.contacts list-row:nth-of-type(1)");
		Util.click("detail button-text[name=\"buttonFriend\"]");
		Util.click("detail button-text[onclick*=\"sendRequestForFriendship\"]");
		Util.click("dialog-navigation item.search");
		Util.click("search tabBody div.contacts list-row:nth-of-type(1)");
		Util.click("detail button-text[name=\"buttonFriend\"]");
		Util.get("detail text[name=\"friend\"]>div>span");
		Util.click("dialog-navigation item.home");
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
			final int maxWait = 40;
			int i = 0;
			while (driver.findElements(By.cssSelector("main [toggle]")).size() > 0 ||
					driver.findElements(By.cssSelector("main [class*=\"animated\"]")).size() > 0 ||
					driver.findElements(By.cssSelector("content[class*=\"SlideOut\"]")).size() > 0) {
				if (i++ > maxWait)
					throw new RuntimeException("Timeout during animation, tried to get " + path);
				sleep(100);
			}
			List<?> list;
			i = 0;
			while (true) {
				list = (List<?>) ((JavascriptExecutor) driver).executeScript("return ui.qa('" + path + "')");
				if (list == null || list.size() == 0) {
					if (i++ > maxWait)
						throw new RuntimeException("Timeout during finding element " + path);
					sleep(100);
				} else
					return (WebElement) list.get(0);
			}
		}

		static void click(final String id) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click()", get(id));
		}

		static void sendKeys(final String path, final String keys) {
			for (int i = 0; i < 5; i++) {
				try {
					get(path);
					((JavascriptExecutor) driver)
							.executeScript("var e=ui.q('" + path + "');e.value='" + keys.replace("\n", "\\n") +
									"';e.dispatchEvent(new KeyboardEvent('keyup',{'key':'Shift'}));");
					return;
				} catch (final ElementNotInteractableException e) {
					sleep(250);
				}
			}
			throw new RuntimeException("Failed to send keys " + keys + " for path " + path);
		}
	}
}
