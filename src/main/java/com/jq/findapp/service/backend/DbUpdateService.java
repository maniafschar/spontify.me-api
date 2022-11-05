package com.jq.findapp.service.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jq.findapp.repository.Repository;

@Service
public class DbUpdateService {
	@Autowired
	private Repository repository;

	public void update() throws Exception {
		repository.executeUpdate(
				"update Contact set age=(YEAR(current_timestamp) - YEAR(birthday) - case when MONTH(current_timestamp) < MONTH(birthday) or MONTH(current_timestamp) = MONTH(birthday) and DAY(current_timestamp) < DAY(birthday) then 1 else 0 end) where birthday is not null");
		repository.executeUpdate(
				"update Contact set version=null where (version='0.9.9' or version='0.9.3') and (id=217 or id=310)");
		repository.executeUpdate(
				"update ContactNotification contactNotification set contactNotification.seen=true where contactNotification.seen=false and (select lastLogin from Contact contact where contact.id=contactNotification.contactId)>contactNotification.createdAt and TIMESTAMPDIFF(HOUR,contactNotification.createdAt,current_timestamp)>24");
	}
}