package com.cs.CodeSync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.cs")
@EnableJpaRepositories(basePackages = "com.cs.repository")
@EntityScan(basePackages = "com.cs.entity")
public class CodeSyncApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeSyncApplication.class, args);
	}

}
