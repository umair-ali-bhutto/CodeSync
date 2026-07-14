package com.cs.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cs.config.CodeSyncLogger;
import com.cs.entity.CodeSyncAudit;
import com.cs.repository.CodeSyncAuditRepository;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class CodeSyncAuditService {

	private final CodeSyncAuditRepository repository;

	public CodeSyncAuditService(CodeSyncAuditRepository repository) {
		this.repository = repository;
	}

	/**
	 * Insert-only audit logging. NEVER throws exception to calling flow.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	@Retry(name = "codeSyncService", fallbackMethod = "auditFallback")
	@CircuitBreaker(name = "codeSyncService", fallbackMethod = "auditFallback")
	public void saveSafely(CodeSyncAudit log) {
		try {
			repository.save(log);
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "AUDIT EXCEPTION", e);
		}
	}

	public void auditFallback(CodeSyncAudit log, Throwable ex) {
		CodeSyncLogger.logInfo(getClass(), "Audit logging failed after retries <------> " + ex.getMessage());
	}
}