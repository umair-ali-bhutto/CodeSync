package com.cs.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.cs.entity.CodeSyncSharedFile;

/**
 * Repository for SharedFile entities.
 */
public interface CodeSyncSharedFileRepository extends JpaRepository<CodeSyncSharedFile, Long> {

	List<CodeSyncSharedFile> findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(String shareKey);

	Optional<CodeSyncSharedFile> findByFileId(String fileId);

	void deleteByFileId(String fileId);

	long countByShareKeyAndIsActiveTrue(String shareKey);

	List<CodeSyncSharedFile> findByIsActiveTrueAndExpiresAtBefore(Timestamp now);

	List<CodeSyncSharedFile> findByIsActiveFalseAndIsFileMovedFalse();

	@Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM CodeSyncSharedFile f WHERE f.isActive = true")
	long sumActiveFileSizes();
}
