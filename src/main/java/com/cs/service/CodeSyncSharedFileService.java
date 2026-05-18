package com.cs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cs.config.CodeSyncLogger;
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
	public static final long MAX_FILE_SIZE = 100L * 1024 * 1024; // get from backend

	private final CodeSyncSharedFileRepository repo;

	@Value("${codesync.upload-dir}")
	private String uploadDir;

	@Value("${codesync.archive-dir}")
	private String archiveDirectory;

	@Value("${codesync.file-expiry.days:0}")
	private long expiryDays;

	@Value("${codesync.file-expiry.hours:0}")
	private long expiryHours;

	@Value("${codesync.file-expiry.minutes:10}")
	private long expiryMinutes;

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
	public CodeSyncSharedFile store(String shareKey, MultipartFile file, String uploaderIp, String uploaderName)
			throws IOException {
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new IllegalArgumentException("File exceeds maximum allowed size of 100 MB.");
		}

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

		String fileId = UUID.randomUUID().toString();
		String safeName = sanitizeFilename(file.getOriginalFilename());
		String storedName = fileId + "_T-" + timestamp + "_" + safeName;

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
		entity.setUploaderIp(uploaderIp);
		entity.setUploaderName(uploaderName);
		entity.setExpiresAt(calculateExpiry());
		return repo.save(entity);
	}

	public long countActiveFiles(String shareKey) {
		return repo.countByShareKeyAndIsActiveTrue(shareKey);
	}

	/**
	 * Returns all files for the given share key as DTOs.
	 */
	public List<SharedFileDTO> listFiles(String shareKey) {
		List<CodeSyncSharedFile> results = repo.findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(shareKey);
		if (results == null)
			return java.util.Collections.emptyList();
		return results.stream()
				.map(f -> new SharedFileDTO(f.getFileId(), f.getOriginalName(), f.getContentType(), f.getFileSize(),
						f.getUploadedAt(), f.getDownloadCount(), f.getLastDownloadedAt(), f.getUploaderIp(),
						f.getUploaderName(), f.getExpiresAt()))
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
		archiveFile(f);
		f.setIsActive(false);
		f.setDeletedAt(new Timestamp(System.currentTimeMillis()));
		repo.save(f);
	}

	@Transactional
	public void incrementDownload(String fileId) {
		repo.incrementDownloadCount(fileId);
	}

	@Transactional
	public int deleteAll(String shareKey) throws IOException {
		List<CodeSyncSharedFile> files = repo.findByShareKeyAndIsActiveTrueOrderByUploadedAtDesc(shareKey);

		Timestamp now = new Timestamp(System.currentTimeMillis());
		for (CodeSyncSharedFile f : files) {
			archiveFile(f);
			f.setIsActive(false);
			f.setDeletedAt(now);
		}
		repo.saveAll(files);
		return files.size();
	}

	@Transactional
	public int expireFiles() {
		Timestamp now = new Timestamp(System.currentTimeMillis());
		List<CodeSyncSharedFile> expired = repo.findByIsActiveTrueAndExpiresAtBefore(now);
		if (expired.isEmpty())
			return 0;

		expired.forEach(f -> {
			archiveFile(f);
			CodeSyncLogger.logInfo("Expired File: "+f.getOriginalName());
			f.setIsActive(false);
			f.setDeletedAt(now);
		});
		repo.saveAll(expired);

		CodeSyncLogger.logInfo("File expiry job: marked " + expired.size() + " file(s) as inactive.");
		return expired.size();
	}

	// ---- Private helpers ----

	// ---- Archive helper ----
	private void archiveFile(CodeSyncSharedFile f) {
		try {
			Path source = Paths.get(f.getStoredPath());
			if (!Files.exists(source))
				return; // already gone, skip silently

			Path archiveDir = Paths.get(archiveDirectory, f.getShareKey());
			Files.createDirectories(archiveDir);

			Path destination = archiveDir.resolve(source.getFileName());

			// If a file with same name already exists in archive, prefix with timestamp
			if (Files.exists(destination)) {
				String ts = String.valueOf(System.currentTimeMillis());
				destination = archiveDir.resolve(ts + "_" + source.getFileName());
			}

			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);

			// Update stored path in entity so DB reflects the new location
			f.setStoredPath(destination.toString());

		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "archiveFile Exception", e);
		}
	}

	private Timestamp calculateExpiry() {
		long totalMinutes = (expiryDays * 24 * 60) + (expiryHours * 60) + expiryMinutes;
		if (totalMinutes <= 0) {
			throw new IllegalStateException("File expiry is misconfigured — total duration is 0. "
					+ "Set at least one of: codesync.file-expiry.days, .hours, or .minutes");
		}
		long expiresAtMillis = System.currentTimeMillis() + (totalMinutes * 60 * 1000);
		return new Timestamp(expiresAtMillis);
	}

	private String sanitizeFilename(String name) {
		if (name == null || name.isBlank())
			return "file";
		// Strip path separators and dangerous characters
		return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
	}
}
