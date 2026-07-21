package com.ai.agents;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAgentsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiAgentsApplication.class, args);
	}

}
