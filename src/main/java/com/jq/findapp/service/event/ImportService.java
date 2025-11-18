package com.jq.findapp.service.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.jq.findapp.service.CronService.Cron;
import com.jq.findapp.service.CronService.CronResult;

@Component
public class ImportService {
	@Autowired
	private ImportMunich importMunich;

	@Autowired
	private ImportKempten importKempten;
  
	public String run() throws Exception {
		return "M: " + this.importMunich.run()
				+ "\nKE: " + this.importKempten.run();
	}
}
