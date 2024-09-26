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

import com.jq.findapp.service.AuthenticationService;
import com.jq.findapp.util.Json;
import com.jq.findapp.util.Strings;

public class Location {
	private final Pattern emailPattern = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*",
			Pattern.CASE_INSENSITIVE);
	private JavascriptExecutor js;
	private List<String> address;
	private List<String> blocked;

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
		blocked = Arrays.asList(Json.toObject(
				IOUtils.toString(AuthenticationService.class.getResourceAsStream("/json/blockedTokens.json"),
						StandardCharsets.UTF_8),
				String[].class));
		WebDriver driver = null;
		try (final BufferedReader reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/sample.txt")));
				final FileOutputStream out = new FileOutputStream(file);) {
			String line;
			driver = AppTest.createWebDriver(800, 700, false);
			js = (JavascriptExecutor) driver;
			driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
			driver.manage().window().setSize(new Dimension(1200, 900));
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				if (Integer.parseInt(s[1]) <= startId)
					continue;
				address = Arrays
						.asList(s[0].toLowerCase().replace(".", "").replace("-", ",").replace(" ", ",")
								.split(","));
				write(out, "-- " + s[1] + " | " + s[0] + "\n");
				try {
					driver.get(url + "?q=" + s[0]);
					final String urlLocation = (String) js.executeScript(
							"return document.getElementsByClassName('bkaPDb')[0]?.querySelector('a')?.getAttribute('href')");
					if (Strings.isEmpty(urlLocation))
						write(out, "-- not found");
					else if (blocked.stream().anyMatch(e -> urlLocation.toLowerCase().contains(e)))
						write(out, "-- blocked: " + urlLocation);
					else
						write(out, "update location set url='" + urlLocation + "', modified_at=now() where id=" + id + ";");
					write(out, "\n\n");
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
		} finally {
			if (driver != null)
				try {
					driver.close();
				} catch (Exception ex) {
				}
		}
	}

	private void lookupSearchResults(final FileOutputStream out, final String id) throws IOException {
		@SuppressWarnings("unchecked")
		final List<String> links = (List<String>) js.executeScript(
				"var v=document.getElementById('center_col')?.querySelectorAll('span>a');return v?Array.from(v).map(e => e.getAttribute('href')):null;");
		if (links != null)
			for (final String urlPage : links) {
				if (!blocked.stream().anyMatch(e -> urlPage.toLowerCase().contains(e))) {
					((WebDriver) js).get(urlPage);
					String urlLocation = js.executeScript("return document.location.href").toString()
							.toLowerCase();
					String html = null;
					boolean found = false;
					if (urlLocation.contains("falstaff.com") || urlLocation.contains("ich-will-essen.de")) {
						html = ((String) js.executeScript("return document.body.innerHTML")).toLowerCase();
						if (html.contains("contact-details__content"))
							html = html.substring(html.indexOf("contact-details__content"));
						final Matcher matcher = Pattern
								.compile(".*(\\bhttps://[A-Z0-9._-]+\\b).*", Pattern.CASE_INSENSITIVE)
								.matcher(html);
						if (matcher.find()) {
							urlLocation = matcher.group(1);
							found = true;
						}
					}
					if (analysePage(out, id, html, urlLocation, !found))
						return;
				}
			}
	}

	private boolean analysePage(final FileOutputStream out, final String id, String html, final String urlLocation,
			final boolean imprint) throws IOException {
		if (imprint) {
			js.executeScript(
					"Array.from(document.querySelectorAll('a')).find(e => e.textContent.toLowerCase().indexOf('impressum')>-1)?.click()");
			return findEmail((String) js.executeScript("return document.body.innerHTML"), urlLocation, id, out);
		}
		final int addressWordsCount = addressWordsCount(address, html);
		write(out, "-- " + addressWordsCount + "% " + urlLocation + "\n");
		if (addressWordsCount > 70 && findEmail(html, urlLocation, id, out))
			return true;
		return false;
	}

	private int addressWordsCount(final List<String> address, final String html) {
		return (int) (((double) address.stream().filter(e -> html.contains(e.toString())).count()) / address.size()
				* 100 + 0.5);
	}

	private void write(FileOutputStream out, String msg) throws IOException {
		out.write(msg.getBytes(StandardCharsets.UTF_8));
	}

	private boolean findEmail(String html, final String urlLocation, final String id, final FileOutputStream out)
			throws IOException {
		html = html.toLowerCase().replace("[at]", "@").replace("(*at*)", "@");
		int pos = html.length();
		while ((pos = html.lastIndexOf('@', pos - 1)) > 0) {
			final Matcher matcher = emailPattern.matcher(
					html.substring(Math.max(0, pos - 200), Math.min(pos + 200, html.length())));
			if (matcher.find()) {
				final String email = matcher.group(1);
				if (!email.endsWith(".png") && !email.endsWith(".jpg")
						&& (email.endsWith("@web.de") || email.endsWith("@gmx.de") || email.endsWith("@t-online.de")
								|| urlLocation.contains(email.substring(matcher.group(1).indexOf("@") + 1)) ||
								urlLocation.contains(email.substring(0, matcher.group(1).indexOf("@"))))) {
					write(out, "update location set email='"
							+ email + "', url='" + urlLocation + "', modified_at=now() where id=" + id
							+ ";\n");
					return true;
				}
			}
		}
		return false;
	}
}
