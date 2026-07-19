package com.cs.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cs.service.SystemCommandService;

//import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;

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

	@Autowired
	private SystemCommandService commandService;

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

		String memory = commandService.execute(
				"Get-CimInstance Win32_OperatingSystem | ForEach-Object { "
						+ "$total=$_.TotalVisibleMemorySize/1024/1024; " + "$free=$_.FreePhysicalMemory/1024/1024; "
						+ "$used=$total-$free; " + "Write-Host ('Total: {0:N2} GB' -f $total); "
						+ "Write-Host ('Used: {0:N2} GB' -f $used); " + "Write-Host ('Free: {0:N2} GB' -f $free) }",
				"free -m | awk '/Mem:/ {printf \"Total: %.2f GB  Used: %.2f GB  Free: %.2f GB   Usage: %.2f%%\\n\",$2/1024,($2-$7)/1024,$7/1024,(($2-$7)/$2)*100}'",
				20);

		CodeSyncLogger.logInfo("💾 MEMORY => " + memory.trim());

		String cpu = commandService.execute(
				"Write-Host 'Cores:' (Get-CimInstance Win32_Processor).NumberOfLogicalProcessors; "
						+ "Write-Host 'Usage:' ((Get-Counter '\\Processor(_Total)\\% Processor Time').CounterSamples.CookedValue).ToString('0.00') '%' ",
				"printf \"Cores: %s \\t Usage: %s%%\\n\" \"$(nproc)\" \"$(top -bn1 | awk '/Cpu\\(s\\)/ {print 100-$8}')\"",
				20);

		CodeSyncLogger.logInfo("🧠 CPU => " + cpu.trim());
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
		// For mysql
//		AbandonedConnectionCleanupThread.checkedShutdown();
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
