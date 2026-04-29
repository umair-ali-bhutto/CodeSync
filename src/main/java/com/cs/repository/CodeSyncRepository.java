package com.cs.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cs.entity.CodeSync;

public interface CodeSyncRepository extends JpaRepository<CodeSync, Long> {

	Optional<CodeSync> findByShareKey(String shareKey);

}
