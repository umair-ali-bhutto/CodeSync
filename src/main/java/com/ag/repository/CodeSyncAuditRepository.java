package com.ag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ag.entity.CodeSyncAudit;

public interface CodeSyncAuditRepository extends JpaRepository<CodeSyncAudit, Long> {
}