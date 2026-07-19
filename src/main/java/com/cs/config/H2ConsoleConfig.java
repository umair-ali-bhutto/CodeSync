package com.cs.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class H2ConsoleConfig {

	@Value("${spring.h2.console.path:/h2-console}")
	private String consolePath;

	@Value("${spring.h2.console.settings.web-allow-others:false}")
	private boolean webAllowOthers;

	@Value("${spring.h2.console.settings.trace:false}")
	private boolean trace;

	@Value("${spring.h2.console.load-on-startup:1}")
	private int loadOnStartup;

	@Bean
	@ConditionalOnProperty(prefix = "spring.h2.console", name = "enabled", havingValue = "true")
	public ServletRegistrationBean<JakartaWebServlet> h2ConsoleServlet() {

		CodeSyncLogger.logDebug("Registering H2 Console on: " + consolePath);

		ServletRegistrationBean<JakartaWebServlet> bean = new ServletRegistrationBean<>(new JakartaWebServlet(),
				consolePath.endsWith("/*") ? consolePath : consolePath + "/*");

		bean.addInitParameter("-webAllowOthers", String.valueOf(webAllowOthers));
		bean.addInitParameter("-trace", String.valueOf(trace));

		bean.setLoadOnStartup(loadOnStartup);

		return bean;
	}
}