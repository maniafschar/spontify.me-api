package com.jq.findapp.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Log.LogStatus;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.LogFilter;
import com.jq.findapp.util.Strings;

@Service
public class CronService {
	private static final Set<String> running = ConcurrentHashMap.newKeySet();

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private Repository repository;

	// 13 executable jobs
	@Autowired
	private ChatService chatService;

	@Autowired
	private DbService dbService;

	@Autowired
	private EngagementService engagementService;

	@Autowired
	private EventService eventService;

	@Autowired
	private ImportLocationsService importLocationsService;

	@Autowired
	private ImportLogService importLogService;

	@Autowired
	private ImportSportsBarService importSportsBarService;

	@Autowired
	private IpService ipService;

	@Autowired
	private MarketingService marketingService;

	@Autowired
	private MatchDayService matchDayService;

	@Autowired
	private MarketingLocationService marketingLocationService;

	@Autowired
	private RssService rssService;

	@Autowired
	private SitemapService sitemapService;

	public static enum Group {
		One, Two, Three, Four, Five
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(value = ElementType.METHOD)
	public static @interface Job {
		Group group() default Group.One;

		/**
		 * 1 2 3 4 5
		 * ┬ ┬ ┬ ┬ ┬
		 * │ │ │ │ │
		 * │ │ │ │ └──── Weekday (1-7, Sunday = 7)
		 * │ │ │ └────── Month (1-12)
		 * │ │ └──────── Day (1-31)
		 * │ └────────── Hour (0-23)
		 * └──────────── Minute (0-59)
		 * 
		 * e.g.: 10,40 0 1,15
		 */
		String cron() default "";
	}

	public static class CronResult {
		public String body = "";
		public Exception exception;

		@Override
		public String toString() {
			return ((body == null ? "" : body)
					+ (exception == null ? "" : "\n" + Strings.stackTraceToString(exception))).trim();
		}
	}

	private class JobExecuter {
		private final Method method;
		private final Object service;

		private JobExecuter(final Object service, final Method method) {
			this.method = method;
			this.service = service;
		}

		private CompletableFuture<Void> execute() {
			return CompletableFuture.supplyAsync(() -> {
				final Log log = new Log();
				log.setContactId(BigInteger.ZERO);
				log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
				String name = service.getClass().getSimpleName();
				if (name.contains("$"))
					name = name.substring(0, name.indexOf('$'));
				log.setUri("/support/cron/" + name + "/" + method.getName());
				try {
					if (running.contains(name + '.' + method.getName()))
						log.setStatus(LogStatus.ErrorServiceRunning);
					else {
						running.add(name + '.' + method.getName());
						final CronResult result = (CronResult) method.invoke(service);
						log.setStatus(Strings.isEmpty(result.exception) ? LogStatus.Ok : LogStatus.ErrorServer);
						if (result.body != null)
							log.setBody(result.body.trim());
						if (result.exception != null) {
							log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
									+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
							notificationService.createTicket(TicketType.ERROR, "cron", result.toString(), null);
						}
					}
				} catch (final Throwable ex) {
					log.setStatus(LogStatus.ErrorRuntime);
					log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
							(Strings.isEmpty(log.getBody()) ? "" : "\n" + log.getBody()));
					notificationService.createTicket(TicketType.ERROR, "cron",
							"uncaught exception:\n" + Strings.stackTraceToString(ex)
									+ (Strings.isEmpty(log.getBody()) ? "" : "\n\n" + log.getBody()),
							null);
				} finally {
					running.remove(name + '.' + method.getName());
					log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
					try {
						repository.save(log);
					} catch (final Exception e2) {
						throw new RuntimeException(e2);
					}
				}
				return null;
			});
		}
	}

	private void list(final Object service, final Map<Group, List<JobExecuter>> map) {
		final ZonedDateTime now = Instant.now().atZone(ZoneId.of("Europe/Berlin"));
		for (final Method method : service.getClass().getDeclaredMethods()) {
			if (method.isAnnotationPresent(Job.class)) {
				final Job job = method.getAnnotation(Job.class);
				if (cron(job.cron(), now)) {
					if (CronResult.class.equals(method.getReturnType()) && method.getParameterCount() == 0) {
						if (!map.containsKey(job.group()))
							map.put(job.group(), new ArrayList<>());
						map.get(job.group()).add(new JobExecuter(service, method));
					} else
						notificationService.createTicket(TicketType.ERROR, "CronDeclaration",
								service.getClass().getName() + "." + method + " not eligable for @Job annotation!",
								null);
				}
			}
		}
	}

	public CronResult run(final String classname) throws Exception {
		final String[] s = classname.split("\\.");
		final Object clazz = getClass().getDeclaredField(s[0]).get(this);
		final Method method = clazz.getClass().getDeclaredMethod(s[1]);
		if (!CronResult.class.equals(method.getReturnType()) || !method.isAnnotationPresent(Job.class))
			return null;
		final CronResult result = (CronResult) method.invoke(clazz);
		LogFilter.body.set(result.toString());
		return result;
	}

	@Async
	public void run() {
		final Map<Group, List<JobExecuter>> map = new HashMap<>();
		list(chatService, map);
		list(dbService, map);
		list(engagementService, map);
		list(eventService, map);
		list(importLocationsService, map);
		list(importLogService, map);
		list(importSportsBarService, map);
		list(ipService, map);
		list(marketingLocationService, map);
		list(marketingService, map);
		list(matchDayService, map);
		list(rssService, map);
		list(sitemapService, map);
		Arrays.asList(Group.values()).forEach(e -> {
			if (map.containsKey(e)) {
				final List<CompletableFuture<Void>> list = new ArrayList<>();
				map.get(e).forEach(e2 -> list.add(e2.execute()));
				CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e2 -> list.stream()
						.map(CompletableFuture::join).collect(Collectors.toList())).join();
			}
		});
	}

	boolean cron(final String cron, final ZonedDateTime now) {
		if (Strings.isEmpty(cron))
			return true;
		final String[] s = (cron.trim() + " * * * *").split(" ");
		return match(s[0], now.getMinute())
				&& match(s[1], now.getHour())
				&& match(s[2], now.getDayOfMonth())
				&& match(s[3], now.getMonth().getValue())
				&& match(s[4], now.getDayOfWeek().getValue());
	}

	private boolean match(final String field, final int value) {
		if ("*".equals(field))
			return true;
		final String[] s = field.split(",");
		for (int i = 0; i < s.length; i++) {
			if (s[i].contains("/")) {
				if (value % Integer.parseInt(s[i].split("/")[1]) == 0)
					return true;
			} else if (Integer.parseInt(s[i]) == value)
				return true;
		}
		return false;
	}
}