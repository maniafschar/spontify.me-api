package com.jq.findapp.selenium;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;

import com.jq.findapp.selenium.AppTest.Util;

public class Video {
	private static final int width = 480, height = 720;
	private static final String dir = "screenshots/";
	private static JavascriptExecutor js;

	@BeforeAll
	public static void start() throws Exception {
		new File(dir).mkdir();
		Util.driver = AppTest.createWebDriver(width, height + 200, true);
		js = (JavascriptExecutor) Util.driver;
		Util.driver.manage().window().setPosition(new Point(100, 0));
		Util.driver.get("https://after-work.events/");
	}

	@AfterAll
	public static void stop() throws Exception {
		Util.driver.close();
	}

	@Test
	public void run() throws Exception {
		login();
		app();
		marketing();
	}

	private void app() {
		js.executeScript("var style = document.createElement('style');"
				+ "document.head.appendChild(style);"
				+ "style.sheet.insertRule('hint close {display:none;}');");
		js.executeScript(
				"intro.openHint({ desc: 'Mit Fanclub eröffnen Sie sich neue Marketingmöglichkeiten!<br/><br/>Aber der Reihe nach, als erstes stellen wir Ihnen die App aus Fan-Sicht vor.', pos: '5%,5em', size: '90%,auto' });");
		Util.sleep(5000);
		js.executeScript(
				"intro.openHint({ desc: 'Sie sehen hier Events von Fans für Fans, z.B. einen Frühschoppen vor einem Spiel, etc.<br/><br/>Natürlich sehen Fans auch andere Fans in der Umgebung.', pos: '5%,7.5em', size: '90%,auto', hinkyClass: 'bottom', hinky: 'left:50%;margin-left:-0.5em;' });");
		Util.sleep(5000);
		js.executeScript(
				"intro.openHint({ desc: 'Stellen Sie sich vor Fans sind breuflich in einer fremdem Stadt und möchten trotzdem ein Spiel mit anderen Fans schauen.<br/><br/>Über die Suche können Fans nach Events und anderen Fans mit ähnlichen Interessen, z.B. mountainbiken, philosophieren, etc. suchen.', pos: '5%,-5em', size: '90%,auto', hinkyClass: 'bottom', hinky: 'left:35%;' });");
		Util.sleep(5000);
		js.executeScript("ui.navigation.goTo('search')");
		Util.sleep(1000);
		Util.sendKeys("search div.contacts input[name=\"keywords\"]", "");
		Util.sleep(100);
		js.executeScript("pageSearch.contacts.search()");
		Util.sleep(1000);
		js.executeScript(
				"intro.openHint({ desc: 'Dabei können die Fans sich gegenseitig über Video-Calls schnell und unkompliziert kennenlernen, ohne Telefonnummer oder Email austauschen zu müssen.', pos: '5%,-7em', size: '90%,auto' });");
		Util.sleep(5000);
		js.executeScript(
				"var e=ui.qa('row img[src*=\"contact.svg\"]');for(var i=0;i<e.length;i++)ui.parents(e[i],'row').outerHTML='';");
		js.executeScript(
				"var e=ui.qa('row[i=\"223\"],row[i=\"244\"],row[i=\"276\"],row[i=\"607\"],row[i=\"793\"]');for(var i=0;i<e.length;i++)ui.parents(e[i],'row').outerHTML='';");
		js.executeScript(
				"ui.classRemove('videoCall videochat', 'hidden');var e=ui.q('videoCall').style;e.display='block';e.background='url(https://fan-club.online/images/videoCall.jpg)';e.backgroundSize='"
						+ ((double) 568 / 800 < Double.valueOf(width) / Double.valueOf(height)
								? "100% auto"
								: "auto 100%")
						+ "';e.backgroundPosition='center';ui.q('videoCall call').style.display='none';");
		Util.sleep(5000);
		js.executeScript(
				"ui.q('videoCall').style.display='none';ui.navigation.goTo('home');");
		js.executeScript(
				"intro.openHint({ desc: 'Darüber hinaus sind natürlich Neuigkeiten des Vereins direkt auf der Startseite integriert!', pos: '5%,7em', size: '90%,auto', hinkyClass: 'top', hinky: 'right:0.5em;' });");
		Util.sleep(5000);
		js.executeScript("pageHome.openNews()");
		Util.sleep(5000);
	}

	private void marketing() {
		js.executeScript(
				"intro.openHint({ desc: 'Nun aber zu den Marketingmöglichkeiten des Vereins!', pos: '5%,5em', size: '90%,auto', hinkyClass: 'top', hinky: 'left:0.5em;' });");
		Util.sleep(5000);
		final String orgWindow = Util.driver.getWindowHandle();
		js.executeScript("ui.navigation.openHTML('stats.html','stats')");
		final Set<String> windowHandles = Util.driver.getWindowHandles();
		final WebDriver admin = Util.driver.switchTo().window((String) windowHandles.toArray()[1]);
		admin.manage().window().setPosition(new Point(100, 0));
		Util.sleep(2000);
		final JavascriptExecutor adminJs = (JavascriptExecutor) admin;
		Util.sleep(2000);
		adminJs.executeScript("ui.q('body').appendChild(document.createElement('hint'));");
		adminJs.executeScript(
				"intro.openHint({ desc: 'nun', pos: '5%,5em', size: '90%,auto', hinkyClass: 'top', hinky: 'left:0.5em;' });");
		Util.sleep(5000);
		adminJs.executeScript("ui.open(1)");
		Util.sleep(5000);
		adminJs.executeScript(
				"ui.q('body home mapcanvas').scrollIntoView({ behavior: 'smooth', block: 'center', inline: 'nearest' });"
						+ "ui.q('[aria-label=\"Vergrößern\"]').click();"
						+ "ui.q('[aria-label=\"Vergrößern\"]').click();"
						+ "ui.q('[aria-label=\"Vergrößern\"]').click();"
						+ "ui.q('[aria-label=\"Stadtplan anzeigen\"]').click()");
		Util.sleep(2000);
		adminJs.executeScript("ui.goTo(2)");
		Util.sleep(2000);
		js.executeScript("window.close()");
		Util.driver.switchTo().window(orgWindow);
		js.executeScript("pageLogin.logoff()");
	}

	private void login() throws IOException {
		Util.sleep(2000);
		final String props = IOUtils.toString(Screenshots.class.getResourceAsStream("/application.properties"),
				StandardCharsets.UTF_8);
		final Matcher matcherUser = Pattern.compile(".*app\\.admin\\.screenshot\\.user=([^\n]*).*").matcher(props);
		final Matcher matcherPassword = Pattern.compile(".*app\\.admin\\.screenshot\\.password=([^\n]*).*")
				.matcher(props);
		matcherUser.find();
		matcherPassword.find();
		js.executeScript("pageLogin.login('" + matcherUser.group(1) + "','" + matcherPassword.group(1) + "')");
		js.executeScript("geoData.save({latitude:48.119335544742256,longitude:11.564400465904775})");
		js.executeScript(
				"var p={},e=ui.qa('card img');for(var i=0;i<e.length;i++)if(p[e[i].src])ui.parents(e[i],'card').outerHTML='';else p[e[i].src]=true;");
		js.executeScript(
				"var e=ui.qa('card[onclick*=\"(223,\"],card[onclick*=\"(244,\"],card[onclick*=\"(245,\"],card[onclick*=\"(324,\"],card[onclick*=\"(435,\"],card[onclick*=\"(445,\"],card[onclick*=\"(459,\"],card[onclick*=\"(645,\"],card[onclick*=\"(723,\"],card[onclick*=\"(385,\"],card[onclick*=\"(276,\"],card[onclick*=\"(607,\"],card[onclick*=\"(793,\"]');for(var i=0;i<e.length;i++)e[i].outerHTML='';");
		Util.sleep(1000);
	}
}
