package com.jq.findapp;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.EnableTransactionManagement;

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
		return javaMailSender;
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