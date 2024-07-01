package com.jq.findapp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

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

import com.jq.findapp.util.Utils;

public class LocationTest {
	private JavascriptExecutor driver;

	@BeforeEach
	public void start() throws Exception {
		driver = (JavascriptExecutor) new ChromeDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(50));
		driver.manage().window().setSize(new Dimension(1200, 900));
	}

	@AfterEach
	public void stop() throws Exception {
		driver.close();
	}

	@Test
	public void run() throws Exception {
		try (final BufferedReader reader = new BufferedReader(new FileReader("sample.txt"))) {
			final Pattern email = Pattern.compile(".*(\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,8}\\b).*", Pattern.CASE_INSENSITIVE);
			final String url = "https://www.google.com/search";
			String line;
			while ((line = reader.readLine()) != null) {
				final String s[] = line.split("\\|");
				driver.get(url + "?q=" + s[0]);
				for (int i = 0; i < 5; i++) {
					driver.executeScript("document.getElementById('center_col').querySelectorAll('span>a')[" + i + "].click()");
					driver.executeScript("Array.from(document.querySelectorAll('a')).find(el => el.textContent.toLowerCase().indexOf('impressum')>-1)");
					final String html = driver.executeScript("document.body.innerHTML");
					final Matcher matcher = email.matcher(html);
					if (matcher.find()) {
						System.out.println(s[1] + ": " + matcher.group(1));
						break;
					} else
						driver.executeScript("navigation.back();if(document.location.href.indexOf('" + url + "')<0) navigation.back();");
				}
			}
		} catch (final Exception ex) {
			ex.printStackTrace();
			Util.sleep(600000);
		}
	}
}
