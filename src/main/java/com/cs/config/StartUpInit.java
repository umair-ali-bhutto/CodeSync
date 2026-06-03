package com.cs.config;

import org.springframework.stereotype.Component;

import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Startup initializer responsible for:
 * <ul>
 * <li>Controlling global logging enable/disable flag</li>
 * <li>Ensuring required data is present when the application starts</li>
 * </ul>
 * <p>
 * This component runs automatically during Spring Boot startup and ensures the
 * system has baseline client metadata available.
 */
@Component
public class StartUpInit {

	/**
	 * Global flag used to enable or disable logging dynamically across the
	 * application lifecycle.
	 */
	private static String enableLogs = "Y";

	/**
	 * Executes automatically after the Spring context is initialized.
	 * <p>
	 * Responsible for inserting or updating default client records and enabling
	 * logs for the application runtime.
	 */
	@PostConstruct
	public void init() {
		CodeSyncLogger.logInfo("INIT CALLED");
		setEnableLogs("Y");
	}

	/**
	 * Executes during application shutdown.
	 * <p>
	 * Ensures logging is re-enabled before the application terminates and logs the
	 * shutdown event.
	 */
	@PreDestroy
	public void destroy() {
		setEnableLogs("Y");
		CodeSyncLogger.logInfo("DESTROY CALLED");
		AbandonedConnectionCleanupThread.checkedShutdown();
	}

	/**
	 * Returns the current status of the global logging flag.
	 *
	 * @return "Y" if logging is enabled, otherwise "N"
	 */
	public static String getEnableLogs() {
		return enableLogs;
	}

	/**
	 * Updates the global logging flag used throughout the application.
	 *
	 * @param enableLogs "Y" to enable logging, "N" to disable
	 */
	public static void setEnableLogs(String enableLogs) {
		StartUpInit.enableLogs = enableLogs;
	}

}
