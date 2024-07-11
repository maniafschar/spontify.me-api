package com.jq.findapp.it;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class Location {
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
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			String line;
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				final List<Object> address = Arrays
						.asList(s[0].toLowerCase().replace(".", "").replace("-", ",").replace(" ", ",")
								.split(","));
				driver.get(url + "?q=" + s[0]);
				@SuppressWarnings("unchecked")
				final List<String> links = (List<String>) js.executeScript(
						"return Array.from(document.getElementById('center_col').querySelectorAll('span>a')).map(e => e.getAttribute('href'))");
				for (int i = 0; i < links.size(); i++) {
					System.out.print(s[1] + ": ");
					if (!links.get(i).contains("tripadvisor") &&
							!links.get(i).contains("opentable") &&
							!links.get(i).contains("dasoertliche") &&
							!links.get(i).contains("lasertagarena-oftersheim") &&
							!links.get(i).contains("yelp") &&
							!links.get(i).contains("speisekarte")) {
						driver.get(links.get(i));
						final String urlLocation = js.executeScript("return document.location.href")
								.toString().toLowerCase();
						js.executeScript(
								"Array.from(document.querySelectorAll('a')).find(e => e.textContent.toLowerCase().indexOf('impressum')>-1)?.click()");
						final String html = ((String) js.executeScript("return document.body.innerHTML")).toLowerCase();
						if (address.stream().filter(e -> html.contains(e.toString())).count() > address.size() * 0.7) {
							int pos = html.length();
							while ((pos = html.lastIndexOf('@', pos - 1)) > 0) {
								final Matcher matcher = email.matcher(
										html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length())));
								if (matcher.find() && urlLocation.contains(
										matcher.group(1).toLowerCase().substring(matcher.group(1).indexOf("@") + 1))) {
									System.out.print(matcher.group(1) + " " + urlLocation);
									i = links.size();
									break;
								}
							}
						}
						if (i == links.size() - 1)
							System.out.print(
									(int) (((double) address.stream().filter(e -> html.contains(e.toString())).count())
											/ address.size() * 100 + 0.5) + "%");
					}
					System.out.println(" [" + s[0] + "]");
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}
}
