package com.cs.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cs.entity.CodeSyncSharedFile;

/**
 * Repository for SharedFile entities.
 */
public interface CodeSyncSharedFileRepository extends JpaRepository<CodeSyncSharedFile, Long> {

	List<CodeSyncSharedFile> findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(String shareKey);

	Optional<CodeSyncSharedFile> findByFileId(String fileId);

	// For incrementing download count via a single query
	@Modifying
	@Query("UPDATE CodeSyncSharedFile f SET f.downloadCount = f.downloadCount + 1, f.lastDownloadedAt = CURRENT_TIMESTAMP WHERE f.fileId = :fileId")
	void incrementDownloadCount(@Param("fileId") String fileId);

	void deleteByFileId(String fileId);

	long countByShareKeyAndIsActiveTrue(String shareKey);

	List<CodeSyncSharedFile> findByIsActiveTrueAndExpiresAtBefore(Timestamp now);
}
