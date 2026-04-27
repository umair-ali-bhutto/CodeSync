package com.ag.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ag.config.CodeSyncLogger;
import com.ag.entity.CodeSyncAudit;
import com.ag.repository.CodeSyncAuditRepository;

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
	public void saveSafely(CodeSyncAudit log) {
		try {
			repository.save(log);
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "AUDIT EXCEPTION", e);
		}
	}
}