package com.jq.findapp.service.backend;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.api.SupportCenterApi.SchedulerResult;
import com.jq.findapp.repository.Repository;

@Service
public class DbService {
	@Autowired
	private Repository repository;

	public SchedulerResult update() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/update");
		try {
			final String modifiedAt = Instant.now().minus(Duration.ofSeconds(90)).toString();
			repository.executeUpdate(
					"update Contact set age=cast((YEAR(current_timestamp) - YEAR(birthday) - case when MONTH(current_timestamp) < MONTH(birthday) or MONTH(current_timestamp) = MONTH(birthday) and DAY(current_timestamp) < DAY(birthday) then 1 else 0 end) as short) where birthday is not null");
			repository.executeUpdate(
					"update Contact set active=false where modifiedAt is null or modifiedAt<'"
							+ modifiedAt.substring(0, modifiedAt.indexOf('.')) + "'");
			repository.executeUpdate(
					"update Contact set version=null where (version='0.9.9' or version='0.9.3') and os='android' and language='EN'");
			repository.executeUpdate(
					"update ContactNotification contactNotification set contactNotification.seen=true where contactNotification.seen=false and (select modifiedAt from Contact contact where contact.id=contactNotification.contactId)>contactNotification.createdAt and TIMESTAMPDIFF(MINUTE,contactNotification.createdAt,current_timestamp)>30");
			repository.executeUpdate(
					"update Contact set timezone='Europe/Berlin' where timezone is null");
			final LocalDate d = LocalDate.ofInstant(Instant.now().minus(Duration.ofDays(183)), ZoneId.systemDefault());
			repository.executeUpdate(
					"update ContactToken set token='' where modifiedAt is not null and modifiedAt<'" + d
							+ "' or modifiedAt is null and createdAt<'" + d + "'");
		} catch (Exception e) {
			result.exception = e;
		}
		return result;
	}

	public SchedulerResult backup() {
		final SchedulerResult result = new SchedulerResult(getClass().getSimpleName() + "/backup");
		try {
			new ProcessBuilder("./backup.sh").start().waitFor();
		} catch (Exception e) {
			result.exception = e;
		}
		return result;
	}
}