package com.jq.findapp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class LocationTest {
	private WebDriver driver;

	@BeforeEach
	public void start() throws Exception {
		driver = new ChromeDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		driver.manage().window().setSize(new Dimension(1200, 900));
	}

	@AfterEach
	public void stop() throws Exception {
		driver.close();
	}

	@Test
	public void run() throws Exception {
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/sample.txt")))) {
			final Pattern email = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*",
					Pattern.CASE_INSENSITIVE);
			final String url = "https://www.google.com/search";
			final int max = 20;
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			String line;
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				driver.get(url + "?q=" + s[0]);
				for (int i = 0; i < max; i++) {
					js.executeScript(
							"document.getElementById('center_col').querySelectorAll('span>a')[" + i + "].click()");
					js.executeScript(
							"Array.from(document.querySelectorAll('a')).find(el => el.textContent.toLowerCase().indexOf('impressum')>-1)");
					Thread.sleep(5000);
					String html = (String) js.executeScript("return document.body.innerHTML");
					String urlLocation = (String) js.executeScript("return document.location.href");
					js.executeScript("window.history.back();if(document.location.href.indexOf('"
							+ url + "')<0) window.history.back();");
					int pos = html.lastIndexOf('@');
					if (pos > 0) {
						html = html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length()));
						final Matcher matcher = email.matcher(html);
						if (matcher.find()) {
							System.out.println(s[1] + ": " + matcher.group(1) + " - " + urlLocation + " - " + s[0]);
							break;
						}
					} else if (i == max - 1)
						System.out.println(s[1] + " not found - " + s[0]);
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
			Thread.sleep(10000);
		}
	}
}
