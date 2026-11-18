package com.talentus.scout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TalentusScoutBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(TalentusScoutBeApplication.class, args);
	}

}
