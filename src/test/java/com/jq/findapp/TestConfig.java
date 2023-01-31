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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.sql.DataSource;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.jq.findapp.entity.BaseEntity;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.repository.Repository.Attachment;
import com.jq.findapp.service.ExternalService;

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

	@Component
	public class EndpointsListener implements ApplicationListener<ContextRefreshedEvent> {
		@Override
		public void onApplicationEvent(ContextRefreshedEvent event) {
			final ApplicationContext applicationContext = event.getApplicationContext();
			applicationContext.getBean(RequestMappingHandlerMapping.class).getHandlerMethods()
					.forEach((e, f) -> System.out.println(e + " => " + f));
		}
	}

	@RestController
	@RequestMapping("debug")
	public class DebugApi {
		@Autowired
		private Repository repository;

		@GetMapping("db/{hql}")
		public List<BaseEntity> db(@PathVariable String hql) throws ClassNotFoundException {
			return repository.list(hql);
		}
	}

	@Service
	@Primary
	public class ExternalServiceTest extends ExternalService {
		@Override
		public String google(String param, BigInteger user) {
			try {
				return IOUtils.toString(
						getClass().getResourceAsStream(
								param.startsWith("place/nearbysearch/json?") ? "/googleNearBy.json"
										: param.startsWith("place/photo?") ? "/googlePhoto.html"
												: "/googleResponse.json"),
						StandardCharsets.UTF_8);
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

	public static String substringIndex(Connection connection, String s, String s2, int i) {
		return s;
	}
}