package com.ag.config;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.stereotype.Component;

@Component
public class StartUpInit {

	private static String enableLogs = "Y";

	@PostConstruct
	public void init() {
		CodeSyncLogger.logInfo("INIT CALLED");
		setEnableLogs("N");
	}

	@PreDestroy
	public void destroy() {
		setEnableLogs("Y");
		CodeSyncLogger.logInfo("DESTROY CALLED");
	}

	public static String getEnableLogs() {
		return enableLogs;
	}

	public static void setEnableLogs(String enableLogs) {
		StartUpInit.enableLogs = enableLogs;
	}
}
