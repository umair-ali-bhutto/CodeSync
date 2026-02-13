package com.ag.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ag.entity.CodeSyncClient;

public interface CodeSyncClientRepository extends JpaRepository<CodeSyncClient, String> {
}
