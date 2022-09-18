package com.jq.findapp.service;

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
	}
}
