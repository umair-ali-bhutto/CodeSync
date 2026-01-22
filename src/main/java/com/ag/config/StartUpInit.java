package com.ag.config;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class StartUpInit {

	@PostConstruct
	public void init() {
		CodeSyncLogger.logInfo("INIT CALLED");
	}

	@PreDestroy
	public void destroy() {
		CodeSyncLogger.logInfo("DESTROY CALLED");
	}
}
