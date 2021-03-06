package com.jq.findapp;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FindappApplication {

	public static void main(String[] args) {
		final SpringApplicationBuilder app = new SpringApplicationBuilder(FindappApplication.class);
		app.application().addListeners(new ApplicationPidFileWriter(
				"./shutdown" + System.getProperties().getProperty("server.port") + ".pid"));
		app.run(args);
	}
}