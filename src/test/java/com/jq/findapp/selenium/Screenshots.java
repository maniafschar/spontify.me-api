package com.jq.findapp.selenium;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

import com.jq.findapp.selenium.AppTest.Util;

public class Screenshots {
	private static final String dir = "screenshots/";
	private static JavascriptExecutor js;
	private static final double resolution = 1.0;

	@BeforeAll
	public static void start() throws Exception {
		new File(dir).mkdir();
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

	private String openBrowser(final int width, final int height) {
		if (Util.driver != null)
			Util.driver.close();
		Util.driver = AppTest.createWebDriver(width, height, true);
		js = (JavascriptExecutor) Util.driver;
		Util.driver.manage().window().setSize(new Dimension((int) (width / resolution), (int) (height / resolution)));
		return width + "x" + height;
	}

	private void screenshots(final String name) throws Exception {
		Util.driver.get("https://fan-club.online/");
		Util.sleep(3000);
		js.executeScript("ui.navigation.closeHint()");
		js.executeScript(
				"var p={},e=ui.qa('card img');for(var i=0;i<e.length;i++)if(p[e[i].src])ui.parents(e[i],'card').outerHTML='';else p[e[i].src]=true;");
		js.executeScript(
				"var e=ui.qa('card[onclick*=\"(223,\"],card[onclick*=\"(244,\"],card[onclick*=\"(245,\"],card[onclick*=\"(324,\"],card[onclick*=\"(435,\"],card[onclick*=\"(445,\"],card[onclick*=\"(459,\"],card[onclick*=\"(645,\"],card[onclick*=\"(723,\"],card[onclick*=\"(385,\"],card[onclick*=\"(276,\"],card[onclick*=\"(607,\"],card[onclick*=\"(793,\"]');for(var i=0;i<e.length;i++)e[i].outerHTML='';");
		screenshot(name);
		login();
		Util.sleep(1000);
		js.executeScript("pageHome.openNews()");
		Util.sleep(1000);
		js.executeScript("ui.q('dialog-hint input-checkbox[value=\"9.157\"]').click()");
		Util.sleep(1000);
		screenshot(name + "-news");
		js.executeScript("ui.navigation.goTo('search')");
		Util.sleep(1000);
		Util.click("search tabHeader tab[i=\"contacts\"]");
		Util.sleep(1000);
		js.executeScript("pageSearch.contacts.search()");
		Util.sleep(1000);
		js.executeScript(
				"var e=ui.qa('search div list-row');for(var i=0;i<e.length;i++)if(ui.q('search div list-row[i=\"'+e[i].getAttribute('i')+'\"] svg[class*=\"default\"]'))e[i].outerHTML='';");
		js.executeScript(
				"var e=ui.qa('search div list-row[i=\"223\"],search div list-row[i=\"244\"],search div list-row[i=\"276\"],search div list-row[i=\"607\"],search div list-row[i=\"793\"]');for(var i=0;i<e.length;i++)e[i].outerHTML='';");
		screenshot(name + "-search");
		if (Util.driver.getTitle().contains("Fanclubx")) {
			js.executeScript("ui.navigation.goTo(\"statistics\")");
			screenshot(name + "-stats");
		}
		js.executeScript(
				"ui.classRemove('video-call videochat', 'hidden');var e=ui.q('video-call').style;e.display='block';e.background='url(https://fan-club.online/images/videoCall.jpg)';e.backgroundSize='"
						+ ((double) 568 / 800 < Double.valueOf(name.split("x")[0]) / Double.valueOf(name.split("x")[1])
								? "100% auto"
								: "auto 100%")
						+ "';e.backgroundPosition='center';ui.q('video-call call').style.display='none';");
		screenshot(name + "-video");
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

	private void screenshot(final String name) throws Exception {
		Util.sleep(3000);
		IOUtils.write(((ChromeDriver) Util.driver).getScreenshotAs(OutputType.BYTES),
				new FileOutputStream(dir + name + ".png"));
	}
}
