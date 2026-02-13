package com.ag.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ag.entity.CodeSync;

public interface CodeSyncRepository extends JpaRepository<CodeSync, Long> {

	Optional<CodeSync> findByShareKey(String shareKey);

}
