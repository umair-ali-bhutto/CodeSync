package com.cs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cs.dto.SharedFileDTO;
import com.cs.entity.CodeSyncSharedFile;
import com.cs.repository.CodeSyncSharedFileRepository;

/**
 * Handles file upload, listing, download, and deletion for a share key.
 *
 * Files are stored at:
 * ${codesync.upload-dir}/{shareKey}/{fileId}_{originalName}
 *
 * Configure the root directory in application.properties:
 * codesync.upload-dir=./uploads
 */
@Service
public class CodeSyncSharedFileService {

	/** Max allowed upload size: 100 MB */
	public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

	private final CodeSyncSharedFileRepository repo;

	@Value("${codesync.upload-dir}")
	private String uploadDir;

	public CodeSyncSharedFileService(CodeSyncSharedFileRepository repo) {
		this.repo = repo;
	}

	/**
	 * Stores the uploaded file on disk and saves metadata to the database.
	 *
	 * @param shareKey the share key
	 * @param file     the uploaded multipart file
	 * @return the saved SharedFile entity
	 * @throws IllegalArgumentException if the file exceeds the size limit
	 * @throws IOException              on I/O failure
	 */
	@Transactional
	public CodeSyncSharedFile store(String shareKey, MultipartFile file) throws IOException {
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("File exceeds maximum allowed size of 100 MB.");
		}

		String fileId = UUID.randomUUID().toString();
		String safeName = sanitizeFilename(file.getOriginalFilename());
		String storedName = fileId + "_" + safeName;

		// Create per-shareKey folder
		Path dir = Paths.get(uploadDir, shareKey);
		Files.createDirectories(dir);

		Path destination = dir.resolve(storedName);
		Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

		CodeSyncSharedFile entity = new CodeSyncSharedFile();
		entity.setShareKey(shareKey);
		entity.setFileId(fileId);
		entity.setOriginalName(safeName);
		entity.setContentType(file.getContentType());
		entity.setFileSize(file.getSize());
		entity.setStoredPath(destination.toString());

		return repo.save(entity);
	}

	/**
	 * Returns all files for the given share key as DTOs.
	 */
	public List<SharedFileDTO> listFiles(String shareKey) {
		return repo.findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(shareKey).stream()
				.map(f -> new SharedFileDTO(f.getFileId(), f.getOriginalName(), f.getContentType(), f.getFileSize(),
						f.getUploadedAt(), f.getDownloadCount(), f.getLastDownloadedAt()))
				.collect(Collectors.toList());
	}

	/**
	 * Resolves the file entity by its public fileId.
	 *
	 * @throws IllegalArgumentException if not found
	 */
	public CodeSyncSharedFile findByFileId(String fileId) {
		return repo.findByFileId(fileId).orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));
	}

	/**
	 * Deletes the file from disk and removes its DB record.
	 */
//	@Transactional
//	public void delete(String fileId) throws IOException {
//		/* NOT DELETED FROM FILE SYSTEM FOR NOW --> UMAIR.ALI :-) */
//
	//// CodeSyncSharedFile f = findByFileId(fileId); / Path path =
	/// Paths.get(f.getStoredPath()); / Files.deleteIfExists(path);
//		repo.deleteByFileId(fileId);
//	}

	@Transactional
	public void delete(String fileId) {
		CodeSyncSharedFile f = findByFileId(fileId);
		f.setIsActive(false);
		f.setDeletedAt(new Timestamp(System.currentTimeMillis()));
		repo.save(f);
	}

	@Transactional
	public void incrementDownload(String fileId) {
		repo.incrementDownloadCount(fileId);
	}

	// ---- Private helpers ----

	private String sanitizeFilename(String name) {
		if (name == null || name.isBlank())
			return "file";
		// Strip path separators and dangerous characters
		return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
	}
}
