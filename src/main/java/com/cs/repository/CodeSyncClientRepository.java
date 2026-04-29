package com.cs.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cs.entity.CodeSyncClient;

public interface CodeSyncClientRepository extends JpaRepository<CodeSyncClient, String> {
}
