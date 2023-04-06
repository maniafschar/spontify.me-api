package com.jq.findapp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import com.jq.findapp.IntegrationTest.Util;

public class Screenshots {
	private static final String dir = "screenshots/";
	private static JavascriptExecutor js;
	private static final Map<String, Object> userAgent = new HashMap<>();
	private static final Map<String, Object> deviceMetrics = new HashMap<>();
	private static final ChromeOptions options = new ChromeOptions();
	private static final double resolution = 1.0;

	@BeforeAll
	public static void start() throws Exception {
		new File(dir).mkdir();
		System.setProperty("webdriver.chrome.driver", "../ChromeDriver");
		deviceMetrics.put("pixelRatio", resolution);
		userAgent.put("deviceMetrics", deviceMetrics);
		userAgent.put("pixelRatio", resolution);
		userAgent.put("mobileEmulationEnabled", Boolean.TRUE);
		userAgent.put("userAgent",
				"Mozilla/5.0 (Linux; Android 7.0; SAMSUNG SM-A510F Build/NRD90M) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/5.4 Chrome/51.0.2704.106 Mobile Safari/537.36");
		options.addArguments("--remote-allow-origins=*");
		options.setExperimentalOption("mobileEmulation", userAgent);
	}

	@AfterAll
	public static void stop() throws Exception {
		Util.driver.close();
	}

	@Test
	public void run() throws Exception {
		screenshots(openBrowser(640, 960)); // 3.5
		screenshots(openBrowser(640, 1136)); // 4
		screenshots(openBrowser(750, 1334)); // 4.7
		screenshots(openBrowser(1242, 2208)); // 5.5
		screenshots(openBrowser(1242, 2688)); // 6.5
		screenshots(openBrowser(1290, 2796)); // 6.7
		screenshots(openBrowser(2048, 2732)); // iPad 3. gen
	}

	private String openBrowser(int width, int height) {
		deviceMetrics.put("width", width);
		deviceMetrics.put("height", height);
		if (Util.driver != null)
			Util.driver.close();
		Util.driver = new ChromeDriver(options);
		js = (JavascriptExecutor) Util.driver;
		Util.driver.manage().window().setSize(new Dimension((int) (width / resolution), (int) (height / resolution)));
		return width + "x" + height;
	}

	private void screenshots(String name) throws Exception {
		Util.driver.get("https://skills.community/");
		// Util.driver.get("https://skillvents.com/");
		Util.sleep(3000);
		js.executeScript(
				"var p={},e=ui.qa('card img');for(var i=0;i<e.length;i++)if(p[e[i].src])ui.parents(e[i],'card').outerHTML='';else p[e[i].src]=true;");
		js.executeScript(
				"var e=ui.qa('card[onclick*=\"(223,\"],card[onclick*=\"(244,\"],card[onclick*=\"(245,\"],card[onclick*=\"(324,\"],card[onclick*=\"(435,\"],card[onclick*=\"(445,\"],card[onclick*=\"(459,\"],card[onclick*=\"(645,\"],card[onclick*=\"(723,\"],card[onclick*=\"(385,\"],card[onclick*=\"(276,\"],card[onclick*=\"(607,\"],card[onclick*=\"(793,\"]');for(var i=0;i<e.length;i++)e[i].outerHTML='';");
		screenshot(name);
		login();
		Util.sleep(1000);
		if (Util.driver.getTitle().contains("Fanclub")) {
			js.executeScript("pageHome.openNews()");
			Util.sleep(2000);
			screenshot(name + "-news");
			Util.sleep(1000);
		}
		js.executeScript("ui.navigation.goTo('search')");
		Util.sleep(1000);
		Util.sendKeys("search div.contacts input[name=\"keywords\"]", "");
		Util.sleep(1000);
		js.executeScript("pageSearch.contacts.search()");
		Util.sleep(1000);
		js.executeScript(
				"var e=ui.qa('row img[src*=\"contact.svg\"]');for(var i=0;i<e.length;i++)ui.parents(e[i],'row').outerHTML='';");
		js.executeScript(
				"var e=ui.qa('row[i=\"223\"],row[i=\"244\"],row[i=\"276\"],row[i=\"607\"],row[i=\"793\"]');for(var i=0;i<e.length;i++)ui.parents(e[i],'row').outerHTML='';");
		screenshot(name + "-search");
		if (Util.driver.getTitle().contains("Fanclub")) {
			final String orgWindow = Util.driver.getWindowHandle();
			js.executeScript("ui.navigation.openHTML('stats.html','stats')");
			final Set<String> windowHandles = Util.driver.getWindowHandles();
			Util.driver.switchTo().window((String) windowHandles.toArray()[1]);
			Util.sleep(2000);
			screenshot(name + "-stats");
			Util.driver.switchTo().window(orgWindow);
		}
		js.executeScript("pageLogin.logoff()");
	}

	private void login() throws IOException {
		final String props = IOUtils.toString(Screenshots.class.getResourceAsStream("/application.properties"),
				StandardCharsets.UTF_8);
		final Matcher matcherUser = Pattern.compile(".*app\\.admin\\.screenshot\\.user=([^\n]*).*").matcher(props);
		final Matcher matcherPassword = Pattern.compile(".*app\\.admin\\.screenshot\\.password=([^\n]*).*")
				.matcher(props);
		matcherUser.find();
		matcherPassword.find();
		js.executeScript("pageLogin.login('" + matcherUser.group(1) + "','" + matcherPassword.group(1) + "')");
		js.executeScript("geoData.save({latitude:48.119335544742256,longitude:11.564400465904775})");
	}

	private void screenshot(String name) throws Exception {
		IOUtils.write(((ChromeDriver) Util.driver).getScreenshotAs(OutputType.BYTES),
				new FileOutputStream(dir + name + ".png"));
	}
}
