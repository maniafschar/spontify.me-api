package com.jq.findapp.service.backend;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.repository.Repository;
import com.jq.findapp.util.Strings;

@Service
public class DbService {
	@Autowired
	private Repository repository;

	public String[] update() {
		repository.executeUpdate(
				"update Contact set age=(YEAR(current_timestamp) - YEAR(birthday) - case when MONTH(current_timestamp) < MONTH(birthday) or MONTH(current_timestamp) = MONTH(birthday) and DAY(current_timestamp) < DAY(birthday) then 1 else 0 end) where birthday is not null");
		repository.executeUpdate(
				"update Contact set version=null where (version='0.9.9' or version='0.9.3') and os='android' and language='EN'");
		repository.executeUpdate(
				"update ContactNotification contactNotification set contactNotification.seen=true where contactNotification.seen=false and (select modifiedAt from Contact contact where contact.id=contactNotification.contactId)>contactNotification.createdAt and TIMESTAMPDIFF(MINUTE,contactNotification.createdAt,current_timestamp)>30");
		return new String[] { getClass().getSimpleName() + "/update", null };
	}

	public String[] backup() {
		final String[] result = new String[] { getClass().getSimpleName() + "/backup", null };
		try {
			new ProcessBuilder("./backup.sh").start().waitFor();
		} catch (InterruptedException | IOException e) {
			result[1] = Strings.stackTraceToString(e);
		}
		return result;
	}
}