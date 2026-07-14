package com.cs.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.ServletContext;

@Configuration
public class OpenApiConfig {

	@Autowired
	private ServletContext servletContext;

	@Bean
	public OpenAPI codeSyncOpenAPI() {
		String contextPath = servletContext.getContextPath(); // dynamic

		return new OpenAPI()
				.addServersItem(new Server().url(contextPath == null ? "" : contextPath).description("Local Server"))
				.info(new Info().title("CodeSync API").version("2.0.0")
						.description("REST APIs for CodeSync file + share system"));
	}

	@Bean
	public GroupedOpenApi allEndpoints() {
	    return GroupedOpenApi.builder()
	            .group("all-endpoints")
	            .packagesToScan("com.cs.controller")
	            .pathsToMatch("/**")
	            .build();
	}
	
//	@Bean
//	public GroupedOpenApi restApiGroup() {
//		return GroupedOpenApi.builder().group("all-apis").packagesToScan("com.cs.controller")
//				.pathsToMatch("/api/**", "/logsService").build();
//	}
}