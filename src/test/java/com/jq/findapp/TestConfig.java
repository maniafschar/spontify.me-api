package com.jq.findapp;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.ImageHtmlEmail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.ExternalService;
import com.jq.findapp.service.NotificationService.MailCreateor;
import com.jq.findapp.service.backend.SurveyService;

@Profile("test")
@TestConfiguration
@EnableTransactionManagement
public class TestConfig {
	@Bean
	public DataSource getDataSource() throws Exception {
		final DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUrl("jdbc:h2:mem:db;DB_CLOSE_DELAY=-1");
		dataSource.getConnection()
				.prepareStatement(
						"CREATE ALIAS IF NOT EXISTS WEEKDAY FOR \"" + getClass().getName() + ".weekday\";")
				.executeUpdate();
		dataSource.getConnection()
				.prepareStatement(
						"CREATE ALIAS IF NOT EXISTS TO_DAYS FOR \"" + getClass().getName() + ".toDays\";")
				.executeUpdate();
		dataSource.getConnection()
				.prepareStatement(
						"CREATE ALIAS IF NOT EXISTS SUBSTRING_INDEX FOR \"" + getClass().getName()
								+ ".substringIndex\";")
				.executeUpdate();
		Files.createDirectories(Paths.get(Attachment.PATH));
		Files.createDirectories(Paths.get(Attachment.PATH + Attachment.PUBLIC));
		return dataSource;
	}

	@Bean
	@Primary
	public MailCreateor mailCreator() throws EmailException {
		final MailCreateor mailCreateor = mock(MailCreateor.class);
		doAnswer(e -> {
			final ImageHtmlEmail imageHtmlEmail = spy(new ImageHtmlEmail());
			doAnswer(f -> {
				imageHtmlEmail.buildMimeMessage();
				new File("target/email/").mkdir();
				new FileOutputStream("target/email/" + System.currentTimeMillis() + Math.random())
						.write(IOUtils.toByteArray(imageHtmlEmail.getMimeMessage().getInputStream()));
				return "";
			}).when(imageHtmlEmail).send();
			return imageHtmlEmail;
		}).when(mailCreateor).create();
		return mailCreateor;
	}

	@RestController
	@RequestMapping("debug")
	public class DebugApi {
		@Autowired
		private Repository repository;

		@GetMapping("db/{hql}")
		public List<BaseEntity> db(@PathVariable final String hql) throws ClassNotFoundException {
			return repository.list(hql);
		}
	}

	@Service
	@Primary
	public class ExternalServiceTest extends ExternalService {
		@Override
		public String google(final String param) {
			try {
				return IOUtils.toString(
						getClass().getResourceAsStream(
								param.startsWith("place/nearbysearch/json?") ? "/googleNearBy.json"
										: param.startsWith("place/photo?") ? "/googlePhoto.html"
												: "/googleResponse.json"),
						StandardCharsets.UTF_8);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Service
	@Primary
	public class EventServiceMock extends EventService {
		@Override
		protected String get(final String url) {
			try {
				return IOUtils.toString(
						getClass().getResourceAsStream(url.contains("/veranstaltungen/")
								? (url.split("/").length == 5 ? "/eventList.html" : "/eventDetail.html")
								: "/eventAddress.html"),
						StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Service
	@Primary
	public class SurveyServiceMock extends SurveyService {
		public int offset = 0;

		@Override
		protected JsonNode get(final String url) throws Exception {
			String s = IOUtils.toString(
					getClass().getResourceAsStream(
							url.startsWith("id=") ? "/surveyLastMatch.json" : "/surveyMatchdays.json"),
					StandardCharsets.UTF_8);
			Instant date = Instant.now();
			if (offset != -1 && url.contains("season=")) {
				for (int i = 0; i < LocalDate.now().getYear()
						- Integer.valueOf(url.substring(url.indexOf("season=") + 7)); i++)
					date = date.minus(Duration.ofDays(365));
				if (LocalDate.now().getYear() > date.atZone(ZoneId.systemDefault()).getYear())
					s = s.replace("\"NS\"", "\"FT\"");
			}
			return new ObjectMapper().readTree(
					s.replace("\"{date}\"", "" + (long) (date.getEpochSecond() + offset)))
					.get("response");
		}
	}

	public static Timestamp toDays(final Connection connection, final Timestamp timestamp)
			throws SQLException {
		return timestamp;
	}

	public static int weekday(final Connection connection, final Timestamp timestamp)
			throws SQLException {
		return 1;
	}

	public static String substringIndex(final Connection connection, final String s, final String s2, final int i) {
		return s;

	}
}
