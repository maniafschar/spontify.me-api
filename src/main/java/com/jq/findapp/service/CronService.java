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
	private AiService aiService;

	@Autowired
	private Repository repository;

	// executable jobs
	@Autowired
	private ChatService chatService;

	@Autowired
	private ClientNewsService clientNewsService;

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

	/**
	 * <pre>
	 * 1 2 3 4 5
	 * ┬ ┬ ┬ ┬ ┬
	 * │ │ │ │ │
	 * │ │ │ │ └──── Weekday (1-7, Sunday = 7)
	 * │ │ │ └────── Month (1-12)
	 * │ │ └──────── Day (1-31)
	 * │ └────────── Hour (0-23)
	 * └──────────── Minute (0-59)
	 * </pre>
	 * 
	 * e.g.: 10,40 0 1,15
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(value = ElementType.METHOD)
	public static @interface Cron {
		Group group() default Group.One;

		String value() default "";
	}

	public static class CronResult {
		public String body = "";
		public Exception exception;

		@Override
		public String toString() {
			return ((this.body == null ? "" : this.body)
					+ (this.exception == null ? "" : "\n" + Strings.stackTraceToString(this.exception))).trim();
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
				String name = this.service.getClass().getSimpleName();
				if (name.contains("$"))
					name = name.substring(0, name.indexOf('$'));
				log.setUri("/support/cron/" + name + "/" + this.method.getName());
				try {
					if (running.contains(name + '.' + this.method.getName()))
						log.setStatus(LogStatus.ErrorServiceRunning);
					else {
						running.add(name + '.' + this.method.getName());
						final CronResult result = (CronResult) this.method.invoke(this.service);
						log.setStatus(Strings.isEmpty(result.exception) ? LogStatus.Ok : LogStatus.ErrorServer);
						if (result.body != null)
							log.setBody(result.body.trim());
						if (result.exception != null) {
							log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
									+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
							CronService.this.notificationService.createTicket(TicketType.ERROR, "cron",
									result.toString(), null);
						}
					}
				} catch (final Throwable ex) {
					log.setStatus(LogStatus.ErrorRuntime);
					log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
							(Strings.isEmpty(log.getBody()) ? "" : "\n" + log.getBody()));
					CronService.this.notificationService.createTicket(TicketType.ERROR, "cron",
							"uncaught exception:\n" + Strings.stackTraceToString(ex)
									+ (Strings.isEmpty(log.getBody()) ? "" : "\n\n" + log.getBody()),
							null);
				} finally {
					running.remove(name + '.' + this.method.getName());
					log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
					try {
						CronService.this.repository.save(log);
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
		Class<?> clazz = service.getClass();
		if (clazz.getName().contains("$")) {
			try {
				clazz = Class.forName(clazz.getName().split("\\$")[0]);
			} catch (final ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		for (final Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Cron.class)) {
				final Cron cron = method.getAnnotation(Cron.class);
				if (this.cron(cron.value(), now)) {
					if (CronResult.class.equals(method.getReturnType()) && method.getParameterCount() == 0) {
						if (!map.containsKey(cron.group()))
							map.put(cron.group(), new ArrayList<>());
						map.get(cron.group()).add(new JobExecuter(service, method));
					} else
						this.notificationService.createTicket(TicketType.ERROR, "CronDeclaration",
								method.toString() + "\nnot eligable for @Cron annotation",
								null);
				}
			}
		}
	}

	public CronResult run(final String classname) throws Exception {
		final String[] s = classname.split("\\.");
		final Object clazz = this.getClass().getDeclaredField(s[0]).get(this);
		final Method method = clazz.getClass().getDeclaredMethod(s[1]);
		if (!CronResult.class.equals(method.getReturnType()) || !method.isAnnotationPresent(Cron.class))
			return null;
		final CronResult result = (CronResult) method.invoke(clazz);
		LogFilter.body.set(result.toString());
		return result;
	}

	@Async
	public void run() {
		final Map<Group, List<JobExecuter>> map = new HashMap<>();
		this.list(this.chatService, map);
		this.list(this.clientNewsService, map);
		this.list(this.dbService, map);
		this.list(this.engagementService, map);
		this.list(this.eventService, map);
		this.list(this.importLocationsService, map);
		this.list(this.importLogService, map);
		this.list(this.importSportsBarService, map);
		this.list(this.ipService, map);
		this.list(this.marketingLocationService, map);
		this.list(this.marketingService, map);
		this.list(this.matchDayService, map);
		this.list(this.rssService, map);
		this.list(this.sitemapService, map);
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
		return this.match(s[0], now.getMinute())
				&& this.match(s[1], now.getHour())
				&& this.match(s[2], now.getDayOfMonth())
				&& this.match(s[3], now.getMonth().getValue())
				&& this.match(s[4], now.getDayOfWeek().getValue());
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