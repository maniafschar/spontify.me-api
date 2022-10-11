package com.jq.findapp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.jq.findapp.service.ExternalService;

@Profile("test")
@Configuration
@EnableTransactionManagement
public class JpaTestConfiguration {
	@Bean
	public DataSource getDataSource() throws SQLException {
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
		return dataSource;
	}

	@Bean
	@Primary
	public JavaMailSender javaMailSender() {
		final JavaMailSender javaMailSender = mock(JavaMailSender.class);
		when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doAnswer(e -> {
			new File("target/email/").mkdir();
			new FileOutputStream("target/email/" + System.currentTimeMillis() + Math.random())
					.write(IOUtils.toByteArray(((MimeMessage) e.getArgument(0)).getInputStream()));
			return null;
		}).when(javaMailSender).send(any(MimeMessage.class));
		return javaMailSender;
	}

	@Service
	@Primary
	public class ExternalServiceTest extends ExternalService {
		@Override
		public String google(String param, BigInteger user) {
			try {
				return IOUtils.toString(getClass().getResourceAsStream("/googleResponse.json"), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static Timestamp toDays(Connection connection, Timestamp timestamp)
			throws SQLException {
		return timestamp;
	}

	public static int weekday(Connection connection, Timestamp timestamp)
			throws SQLException {
		return 1;
	}
}