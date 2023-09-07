package com.jq.findapp;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.Strings;

@Configuration
@EnableWebSocketMessageBroker
public class FindappConfiguration implements AsyncConfigurer, WebSocketMessageBrokerConfigurer {
	private static final List<Integer> SENT_ERRORS = new ArrayList<>();
	private static final String[] allowedOrigins = {
			"https://localhost",
			"app://localhost",
			"https://after-work.events",
			"https://fan-club.online",
			"https://*.fan-club.online",
			"https://offline-poker.com",
			"https://skills.community",
			"https://skillvents.com",
			"http://localhost:9000"
	};

	@Autowired
	private NotificationService notificationService;

	@Override
	public Executor getAsyncExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.initialize();
		return executor;
	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(final CorsRegistry registry) {
				registry.addMapping("/**").allowedOriginPatterns(allowedOrigins)
						.allowedHeaders("clientid", "content-type", "password", "salt", "secret", "user", "webcall")
						.allowedMethods("GET", "PUT", "POST", "OPTIONS", "DELETE");
			}
		};
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new AsyncUncaughtExceptionHandler() {
			@Override
			public void handleUncaughtException(final Throwable ex, final Method method, final Object... params) {
				String msg = method.getName() + "\n\n" + Strings.stackTraceToString(ex);
				final Integer hash = msg.hashCode();
				boolean send = false;
				synchronized (SENT_ERRORS) {
					if (!SENT_ERRORS.contains(hash))
						send = SENT_ERRORS.add(hash);
				}
				if (send) {
					msg = new StringBuilder(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss\n\n").format(new Date())) + msg;
					for (int i = 0; i < params.length; i++)
						msg += "\n\nParameter " + (i + 1) + ":\n" + params[i];
					try {
						notificationService.createTicket(TicketType.ERROR, "async", msg, null, null);
					} catch (final Exception e1) {
						// never happend in 20 years...
					}
				}
			}
		};
	}

	@Override
	public void configureMessageBroker(final MessageBrokerRegistry config) {
		config.enableSimpleBroker("/user");
		config.setApplicationDestinationPrefixes("/ws");
		config.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(final StompEndpointRegistry registry) {
		registry.addEndpoint("/ws/init").setAllowedOriginPatterns(allowedOrigins).withSockJS();
	}
}