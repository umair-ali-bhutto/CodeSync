package com.cs.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cs.entity.CodeSyncBlockedIp;

@Repository
public interface CodeSyncBlockedIpRepository extends JpaRepository<CodeSyncBlockedIp, String> {
	boolean existsByIp(String ip);
}