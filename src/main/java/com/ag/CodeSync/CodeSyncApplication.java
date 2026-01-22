package com.ag.CodeSync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.ag")
@EntityScan(basePackages = "com.ag.entity")
@EnableJpaRepositories(basePackages = "com.ag.repository")
public class CodeSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeSyncApplication.class, args);
	}

}
