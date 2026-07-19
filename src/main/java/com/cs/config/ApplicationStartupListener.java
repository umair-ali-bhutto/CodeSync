package com.cs.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupListener {

	@EventListener(ApplicationReadyEvent.class)
	public void applicationReady() {
		CodeSyncLogger.logInfo("=================================================");
		CodeSyncLogger.logInfo("🚀 CodeSync Application Started Successfully");
		CodeSyncLogger.logInfo("⚡ CODE SYNC IS ONLINE ⚡");
		CodeSyncLogger.logInfo("=================================================");
	}

}