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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Strings;

public class Location {
	private final Pattern emailPattern = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*",
			Pattern.CASE_INSENSITIVE);

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
		final List<String> blocked = Arrays.asList(new ObjectMapper().readValue(
				AuthenticationService.class.getResourceAsStream("/json/blockedTokens.json"),
				String[].class));
		WebDriver driver = null;
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/sample.txt")));
				final FileOutputStream out = new FileOutputStream(file);) {
			String line;
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				if (Integer.parseInt(s[1]) <= startId)
					continue;
				System.out.println(s[1]);
				final List<String> address = Arrays
						.asList(s[0].toLowerCase().replace(".", "").replace("-", ",").replace(" ", ",")
								.split(","));
				write(out, "-- " + s[1] + " | " + s[0] + "\n");
				try {
					driver = AppTest.createWebDriver(800, 700, false);
					final JavascriptExecutor js = (JavascriptExecutor) driver;
					driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
					driver.manage().window().setSize(new Dimension(1200, 900));
					driver.get(url + "?q=" + s[0]);
					@SuppressWarnings("unchecked")
					final List<String> links = (List<String>) js.executeScript(
							"return Array.from(document.getElementById('center_col').querySelectorAll('span>a')).map(e => e.getAttribute('href'))");
					for (final String urlPage : links) {
						if (!blocked.stream().anyMatch(e -> urlPage.toLowerCase().contains(e))) {
							driver.get(urlPage);
							final String urlLocation = js.executeScript("return document.location.href")
									.toString().toLowerCase();
							js.executeScript(
									"Array.from(document.querySelectorAll('a')).find(e => e.textContent.toLowerCase().indexOf('impressum')>-1)?.click()");
							final String html = ((String) js.executeScript("return document.body.innerHTML"))
									.toLowerCase();
							final int addressWordsCount = addressWordsCount(address, html);
							write(out, "-- " + addressWordsCount + "% " + urlPage + "\n");
							if (addressWordsCount > 70 && findEmail(html, urlLocation, s[1], out))
								break;
						}
					}
					write(out, "\n");
				} catch (Exception ex) {
					if (ex.getMessage().contains("Could not start a new session. Response code 500.")) {
						write(out, Strings.stackTraceToString(ex));
						return;
					}
					write(out, "-- " + ex.getClass().getName() + ": " + ex.getMessage().replace('\n', ',') + "\n");
				} finally {
					if (driver != null)
						try {
							driver.close();
						} catch (Exception ex) {
						}
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
		}
	}

	private int addressWordsCount(final List<String> address, final String html) {
		return (int) (((double) address.stream().filter(e -> html.contains(e.toString())).count()) / address.size()
				* 100 + 0.5);
	}

	private void write(FileOutputStream out, String msg) throws IOException {
		out.write(msg.getBytes(StandardCharsets.UTF_8));
	}

	private boolean findEmail(final String html, final String urlLocation, final String id, final FileOutputStream out)
			throws IOException {
		int pos = html.length();
		while ((pos = html.lastIndexOf('@', pos - 1)) > 0) {
			final Matcher matcher = emailPattern.matcher(
					html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length())));
			if (matcher.find() && (urlLocation.contains(
					matcher.group(1).toLowerCase().substring(matcher.group(1).indexOf("@") + 1)) ||
					urlLocation.contains(
							matcher.group(1).toLowerCase().substring(0, matcher.group(1).indexOf("@"))))) {
				write(out, "update location set email='"
						+ matcher.group(1) + "', url='" + urlLocation + "', modified_at=now() where id=" + id + ";\n");
				return true;
			}
		}
		return false;
	}
}
