package ru.z3r0ing.discordlp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiscordlpApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiscordlpApplication.class, args);
	}

}
