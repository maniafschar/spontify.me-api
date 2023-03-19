package com.jq.findapp;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.jq.findapp.IntegrationTest.Util;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = { FindappApplication.class, TestConfig.class })
@ActiveProfiles("test")
public class Screenshots {
	private static JavascriptExecutor js;
	private static String url = "https://skillvents.com/";

	@Value("${app.admin.screenshot.user}")
	protected String user;

	@Value("${app.admin.screenshot.password}")
	protected String password;

	@BeforeAll
	public static void start() throws Exception {
		System.setProperty("webdriver.chrome.driver", "../ChromeDriver");
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--remote-allow-origins=*");
		Util.driver = new ChromeDriver(options);
		js = (JavascriptExecutor) Util.driver;
		Util.driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		Util.driver.manage().window().setSize(new Dimension(450, 800));
	}

	@AfterAll
	public static void stop() throws Exception {
		Util.driver.close();
	}

	@Test
	public void run() throws Exception {
		try {
			init();
			Util.sleep(3000);
			js.executeScript("screenshot('home')");
			js.executeScript("pageLogin.login('" + user + "', '" + password + "');");
			Util.sleep(1000);
			js.executeScript("ui.navigation.goTo('search')");
			Util.sleep(1000);
			Util.sendKeys("search div.contacts input[name=\"keywords\"]", "fc bayern");
			Util.sleep(1000);
			js.executeScript("pageSearch.contacts.search()");
			Util.sleep(1000);
			js.executeScript("screenshot('search', true)");
			Util.sleep(10000);
		} catch (Exception ex) {
			Util.sleep(10000);
			throw ex;
		}
	}

	private void init() throws Exception {
		Util.driver.get(url);
		js.executeScript("var script = document.createElement('script');" +
				"script.src = 'js/screenshots.js';" +
				"script.onload = function(){init()};" +
				"document.head.appendChild(script);");
	}
}
