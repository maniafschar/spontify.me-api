package com.jq.findapp.service.backend;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.jq.findapp.entity.Log;
import com.jq.findapp.entity.Log.LogStatus;
import com.jq.findapp.entity.Ticket.TicketType;
import com.jq.findapp.repository.Repository;
import com.jq.findapp.service.ChatService;
import com.jq.findapp.service.EventService;
import com.jq.findapp.service.NotificationService;
import com.jq.findapp.util.LogFilter;
import com.jq.findapp.util.Strings;

@Service
public class CronService {
	@Autowired
	private Repository repository;

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
	private NotificationService notificationService;

	@Autowired
	private RssService rssService;

	@Autowired
	private SitemapService sitemapService;

	private static final Set<String> running = new HashSet<>();

	public CronResult run(final String classname) throws Exception {
		final String[] s = classname.split("\\.");
		final Object clazz = getClass().getDeclaredField(s[0]).get(this);
		final CronResult result = (CronResult) clazz.getClass()
				.getDeclaredMethod("run" + (s.length > 1 ? s[1] : ""))
				.invoke(clazz);
		LogFilter.body.set(result.toString());
		return result;
	}

	@Async
	public void run() {
		CompletableFuture.supplyAsync(() -> {
			final ZonedDateTime now = Instant.now().atZone(ZoneId.of("Europe/Berlin"));
			final List<CompletableFuture<Void>> list = new ArrayList<>();
			run(importSportsBarService, null, list, "0 3", now);
			run(importSportsBarService, "Import", list, null, now);
			run(chatService, null, list, null, now);
			run(marketingLocationService, null, list, "* 6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21", now);
			run(marketingLocationService, "Sent", list, "10 19", now);
			run(marketingLocationService, "Unfinished", list, "30 17", now);
			run(marketingLocationService, "Cooperation", list, "40 17", now);
			run(dbService, null, list, null, now);
			run(dbService, "CleanUp", list, "30 0", now);
			run(engagementService, "Registration", list, "40 10", now);
			run(eventService, null, list, null, now);
			run(eventService, "Match", list, null, now);
			run(eventService, "Import", list, "40 5", now);
			run(eventService, "Publish", list, null, now);
			run(eventService, "Series", list, "40 23", now);
			run(importLogService, null, list, null, now);
			run(rssService, null, list, null, now);
			run(matchDayService, null, list, null, now);
			run(importLocationsService, null, list, null, now);
			run(importLocationsService, "Image", list, "30 2", now);
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			list.clear();
			run(marketingService, null, list, null, now);
			run(marketingService, "Result", list, null, now);
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(engagementService, "NearBy", null, null, now);
			list.clear();
			run(engagementService, null, list, null, now);
			run(ipService, null, list, null, now);
			run(sitemapService, null, list, "0 20", now);
			CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()])).thenApply(e -> list.stream()
					.map(CompletableFuture::join).collect(Collectors.toList())).join();
			run(dbService, "Backup", null, null, now);
			return null;
		});
	}

	private void run(final Object bean, final String method, final List<CompletableFuture<Void>> list,
			final String cron, final ZonedDateTime now) {
		if (!cron(cron, now))
			return;
		final CompletableFuture<Void> e = CompletableFuture.supplyAsync(() -> {
			final Log log = new Log();
			log.setContactId(BigInteger.ZERO);
			log.setCreatedAt(new Timestamp(Instant.now().toEpochMilli()));
			final String m = "run" + (method == null ? "" : method);
			String name = bean.getClass().getSimpleName();
			if (name.contains("$"))
				name = name.substring(0, name.indexOf('$'));
			log.setUri("/support/cron/" + name + "/" + m);
			try {
				if (running.contains(name + '.' + m))
					log.setStatus(LogStatus.Running);
				else {
					running.add(name + '.' + m);
					final CronResult result = (CronResult) bean.getClass().getMethod(m).invoke(bean);
					log.setStatus(Strings.isEmpty(result.exception) ? LogStatus.Ok : LogStatus.Error);
					if (result.body != null)
						log.setBody(result.body.trim());
					if (result.exception != null) {
						log.setBody((log.getBody() == null ? "" : log.getBody() + "\n")
								+ result.exception.getClass().getName() + ": " + result.exception.getMessage());
						notificationService.createTicket(TicketType.ERROR, "cron", result.toString(), null);
					}
				}
			} catch (final Throwable ex) {
				log.setStatus(LogStatus.Exception);
				log.setBody("uncaught exception " + ex.getClass().getName() + ": " + ex.getMessage() +
						(Strings.isEmpty(log.getBody()) ? "" : "\n" + log.getBody()));
				notificationService.createTicket(TicketType.ERROR, "cron",
						"uncaught exception:\n" + Strings.stackTraceToString(ex)
								+ (Strings.isEmpty(log.getBody()) ? "" : "\n\n" + log.getBody()),
						null);
			} finally {
				running.remove(name + '.' + m);
				log.setTime((int) (System.currentTimeMillis() - log.getCreatedAt().getTime()));
				if (log.getBody() != null && log.getBody().length() > 255)
					log.setBody(log.getBody().substring(0, 252) + "...");
				try {
					repository.save(log);
				} catch (final Exception e2) {
					throw new RuntimeException(e2);
				}
			}
			return null;
		});
		if (list == null)
			e.join();
		else
			list.add(e);
	}

	/**
	 * 1 2 3 4 5
	 * ┬ ┬ ┬ ┬ ┬
	 * │ │ │ │ │
	 * │ │ │ │ └──── Weekday (1-7, Sunday = 7)
	 * │ │ │ └────── Month (1-12)
	 * │ │ └──────── Day (1-31)
	 * │ └────────── Hour (0-23)
	 * └──────────── Minute (0-59)
	 */
	boolean cron(final String cron, final ZonedDateTime now) {
		if (cron == null)
			return true;
		if (Strings.isEmpty(cron))
			return false;
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

	public static class CronResult {
		public String body = "";
		public Exception exception;

		@Override
		public String toString() {
			return ((body == null ? "" : body)
					+ (exception == null ? "" : "\n" + Strings.stackTraceToString(exception))).trim();
		}
	}
}
