package com.cs.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cs.config.CodeSyncLogger;
import com.cs.repository.CodeSyncClientRepository;

import jakarta.annotation.PostConstruct;

/**
 * In-memory cache for mapping client IP addresses to their corresponding
 * human-readable names.
 * <p>
 * This cache is populated at application startup from the database and is used
 * primarily for logging and audit readability, avoiding repeated database
 * lookups during request processing.
 */
@Service
public class CodeSyncClientCache {

	/**
	 * Thread-safe map storing IP-to-name mappings loaded from the database.
	 */
	private static final Map<String, String> CLIENT_MAP = new ConcurrentHashMap<>();

	/**
	 * Repository used to fetch client IP and name mappings from the database during
	 * application initialization.
	 */
	@Autowired
	private CodeSyncClientRepository repository;

	/**
	 * Loads all client IP-name mappings from the database into the in-memory cache
	 * after the Spring context is initialized.
	 * <p>
	 * This method runs only once at startup and prepares the cache for fast lookup
	 * during request logging.
	 */
	@PostConstruct
	public void loadClients() {
		repository.findAll().forEach(c -> CLIENT_MAP.put(c.getIp(), c.getName()));

		CodeSyncLogger.logInfo("✅ CodeSync Clients Loaded: " + CLIENT_MAP.size());
	}

	/**
	 * Retrieves the client name associated with the provided IP address.
	 * <p>
	 * If no mapping is found, the IP address itself is returned.
	 *
	 * @param ip client IP address
	 * @return mapped client name or the IP if not found
	 */
	public static String getNameByIp(String ip) {
		return CLIENT_MAP.getOrDefault(ip, ip);
	}
}
