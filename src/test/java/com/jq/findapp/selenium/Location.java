package com.jq.findapp.selenium;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

public class Location {
	@Test
	public void run() throws Exception {
		final File file = new File("sample");
		int startId = 0;
		if (file.exists()) {
			final String old = IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8);
			if (old.contains("\n\n-- ")) {
				int p = old.lastIndexOf("\n\n-- ");
				startId = Integer.parseInt(old.substring(p + 5, old.indexOf('|', p)).trim());
			}
		}
		file.delete();
		final String url = "https://www.google.com/search";
		final List<String> blocked = Arrays.asList(Json.toObject(
				IOUtils.toString(AuthenticationService.class.getResourceAsStream("/json/blockedTokens.json"),
						StandardCharsets.UTF_8),
				String[].class));
		WebDriver driver = null;
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/sample.txt")));
				final FileOutputStream out = new FileOutputStream(file);) {
			String line;
			driver = AppTest.createWebDriver(800, 700, false);
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
			driver.manage().window().setSize(new Dimension(1200, 900));
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				if (Integer.parseInt(s[1]) <= startId)
					continue;
				try {
					driver.get(
							url + "?q=" + s[0].replace("str.", "straße").replace("Str.", "Straße").replace(", ", " "));
					if (js.executeScript("return document.getElementById('recaptcha')") != null)
						return;
					if (((Number) js.executeScript(
							"return document.getElementById('main').innerHTML.indexOf('Dauerhaft geschlossen')"))
							.longValue() > 0)
						write(out, "update location set skills='X', modified_at=now() where id=" + s[1] + ";\n");
					else {
						final String urlLocation = (String) js.executeScript(
								"return document.getElementsByClassName('bkaPDb')[0]?.querySelector('a')?.getAttribute('href')||document.getElementsByClassName('ab_button')[0]?.getAttribute('href')");
						if (urlLocation != null && urlLocation.contains("?")
								&& blocked.stream().anyMatch(e -> urlLocation.toLowerCase().contains(e)))
							write(out,
									"-- blocked: update location set url='" + urlLocation
											+ "', modified_at=now() where id=" + s[1]
											+ ";\n");
						else if (urlLocation != null && urlLocation.startsWith("http"))
							write(out,
									"update location set url='" + urlLocation + "', modified_at=now() where id=" + s[1]
											+ ";\n");
					}
					Thread.sleep((long) (1000 + 4000 * Math.random()));
				} catch (Exception ex) {
					if (ex.getMessage().contains("Could not start a new session. Response code 500.")) {
						write(out, Strings.stackTraceToString(ex));
						return;
					}
					write(out, "-- " + ex.getClass().getName() + ": " + ex.getMessage().replace('\n', ',') + "\n\n");
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

	private void write(FileOutputStream out, String msg) throws IOException {
		out.write(msg.getBytes(StandardCharsets.UTF_8));
	}
}