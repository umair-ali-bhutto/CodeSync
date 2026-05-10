package com.cs.repository;

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

	/** All files for a given share key, ordered newest first. */
	List<CodeSyncSharedFile> findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(String shareKey);

	/** Find a file by its public file ID. */
	Optional<CodeSyncSharedFile> findByFileId(String fileId);

	// For incrementing download count via a single query (no stale entity risk)
	@Modifying
	@Query("UPDATE CodeSyncSharedFile f SET f.downloadCount = f.downloadCount + 1, f.lastDownloadedAt = CURRENT_TIMESTAMP WHERE f.fileId = :fileId")
	void incrementDownloadCount(@Param("fileId") String fileId);

	/** Delete a file record by its public file ID. */
	void deleteByFileId(String fileId);
}
