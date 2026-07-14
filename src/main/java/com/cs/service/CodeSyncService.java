package com.cs.service;

import org.springframework.stereotype.Service;

import com.cs.config.CodeSyncLogger;
import com.cs.entity.CodeSync;
import com.cs.repository.CodeSyncRepository;

import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import jakarta.transaction.Transactional;

/**
 * Business layer for CodeSync operations.
 */
@Service
@Transactional
public class CodeSyncService {

	private final CodeSyncRepository repository;

	public CodeSyncService(CodeSyncRepository repository) {
		this.repository = repository;
	}

	/**
	 * Fetches an existing share or creates a new one if it does not exist.
	 *
	 * @param shareKey unique share identifier
	 * @return CodeSync entity
	 */
	@Retry(name = "codeSyncService", fallbackMethod = "getOrCreateFallback")
	@CircuitBreaker(name = "codeSyncService", fallbackMethod = "getOrCreateFallback")
	public CodeSync getOrCreate(String shareKey) {
		return repository.findByShareKey(shareKey).orElseGet(() -> {
			CodeSyncLogger.logInfo("Creating new share: " + shareKey);
			CodeSync share = new CodeSync();
			share.setShareKey(shareKey);
			share.setContent("");
			return repository.save(share);
		});
	}

	/**
	 * Updates content for a given share key.
	 *
	 * @param shareKey unique share identifier
	 * @param content  text content
	 * @return updated CodeSync entity
	 */
	public CodeSync update(String shareKey, String content) {
		CodeSync share = getOrCreate(shareKey);
		share.setContent(content);
		return repository.save(share);
	}

	public CodeSync getOrCreateFallback(String shareKey, Throwable ex) {

		CodeSyncLogger.logInfo(getClass(), "Database unavailable <------> " + ex.getMessage());

		CodeSync fallback = new CodeSync();
		fallback.setShareKey(shareKey);
		fallback.setContent("Service temporarily unavailable");

		return fallback;
	}

}
