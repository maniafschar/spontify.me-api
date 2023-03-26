package com.jq.findapp;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

	@Autowired
	private NotificationService notificationService;

	@Override
	public Executor getAsyncExecutor() {
		final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.initialize();
		return executor;
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return new AsyncUncaughtExceptionHandler() {
			@Override
			public void handleUncaughtException(Throwable ex, Method method, Object... params) {
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
						notificationService.createTicket(TicketType.ERROR, "async", msg, null);
					} catch (Exception e1) {
						// never happend in 20 years...
					}
				}
			}
		};
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/user");
		config.setApplicationDestinationPrefixes("/ws");
		config.setUserDestinationPrefix("/user");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws/init").setAllowedOrigins(Strings.URL_APP, Strings.URL_APP_NEW, Strings.URL_LOCALHOST,
				Strings.URL_LOCALHOST_TEST).withSockJS();
	}
}