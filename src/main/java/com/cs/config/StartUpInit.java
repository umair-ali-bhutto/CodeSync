package com.cs.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.cs.entity.CodeSyncClient;
import com.cs.repository.CodeSyncClientRepository;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Startup initializer responsible for:
 * <ul>
 * <li>Loading default client IP-to-name mappings into the database</li>
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
	 * Repository used to perform CRUD operations on
	 * {@link com.cs.entity.CodeSyncClient} for maintaining IP-to-name client
	 * mappings.
	 */
	@Autowired
	private CodeSyncClientRepository repo;

	/**
	 * Executes automatically after the Spring context is initialized.
	 * <p>
	 * Responsible for inserting or updating default client records and enabling
	 * logs for the application runtime.
	 */
	@PostConstruct
	public void init() {
		CodeSyncLogger.logInfo("INIT CALLED");
		syncDefaults();
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

	/**
	 * Inserts or updates a predefined set of trusted client IP-to-name mappings
	 * into the database.
	 * <p>
	 * If a client IP already exists, its name is updated. If it does not exist, a
	 * new record is created.
	 * <p>
	 * This ensures the database always contains the latest known client list
	 * without creating duplicate records.
	 */
	public void syncDefaults() {
		Map<String, String> defaults = new HashMap<>();

		defaults.put("172.191.1.106", "Wasih");
		defaults.put("172.191.1.118", "Ashar");
		defaults.put("172.191.1.134", "Naveed");
		defaults.put("172.191.1.175", "Tanseer");
		defaults.put("172.191.1.184", "Ahsan");
		defaults.put("172.191.1.189", "Zohair");
		defaults.put("172.191.1.198", "Faisal");
		defaults.put("172.191.1.199", "Saad Fazal");
		defaults.put("172.191.1.200", "Internee Windows");
		defaults.put("172.191.1.223", "Umair Ali");
		defaults.put("172.191.1.238", "Azeem");

		defaults.forEach((ip, name) -> {
			CodeSyncClient client = repo.findById(ip).orElseGet(CodeSyncClient::new);
			client.setIp(ip);
			client.setName(name);
			repo.save(client);
		});

		CodeSyncLogger.logInfo("✅ CodeSync clients synced with DB");
	}
}
