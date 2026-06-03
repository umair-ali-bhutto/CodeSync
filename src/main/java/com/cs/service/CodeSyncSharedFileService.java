package com.cs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.cs.config.CodeSyncLogger;
import com.cs.dto.SharedFileDTO;
import com.cs.entity.CodeSyncSharedFile;
import com.cs.exception.FileSizeExceededException;
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

	@Value("${codesync.max-file-size}")
	private DataSize maxFileSize;

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

	@Value("${codesync.winscp.enabled:false}")
	private boolean winScpEnabled;

	@Value("${codesync.winscp.exe-path}")
	private String winScpExePath;

	@Value("${codesync.winscp.sftp-host:}")
	private String winScpHost;

	@Value("${codesync.winscp.sftp-user:}")
	private String winScpUser;

	@Value("${codesync.winscp.sftp-password:}")
	private String winScpPassword;

	@Value("${codesync.winscp.remote-base-path}")
	private String winScpRemoteBasePath;

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

		if (file.getSize() > maxFileSize.toBytes()) {
			throw new FileSizeExceededException(maxFileSize.toMegabytes());
		}

		String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

		String fileId = UUID.randomUUID().toString();
		String safeName = sanitizeFilename(file.getOriginalFilename());
		String storedName = fileId + "_T-" + timestamp + "___" + safeName;

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
	 * Deletes the file from disk and updates its DB record.
	 */
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
			CodeSyncLogger.logInfo("Expired File: " + f.getOriginalName());
			f.setIsActive(false);
			f.setDeletedAt(now);
		});
		repo.saveAll(expired);

		CodeSyncLogger.logInfo("File expiry job: marked " + expired.size() + " file(s) as inactive.");
		return expired.size();
	}

	@Transactional
	public int moveExpiredFiles() {
		List<CodeSyncSharedFile> expired = repo.findByIsActiveFalseAndIsFileMovedFalse();
		if (expired.isEmpty())
			return 0;

		if (winScpEnabled) {
			// Validate WinSCP executable exists before doing anything
			if (!isWinScpAvailable()) {
				CodeSyncLogger.logInfo("moveExpiredFiles: WinSCP not found at path: " + winScpExePath + " — skipping.");
				return 0;
			}

			// Test connectivity before building the full script
			if (!isServerReachable()) {
				CodeSyncLogger.logInfo("moveExpiredFiles: Cannot reach SFTP server " + winScpHost + " — skipping.");
				return 0;
			}

			// Transfer all files in a single WinSCP session
			List<CodeSyncSharedFile> succeeded = transferAllViaWinScp(expired);
			repo.saveAll(expired); // save updated storedPath + isFileMoved for all
			CodeSyncLogger.logInfo(
					"moveExpiredFiles: WinSCP transferred " + succeeded.size() + "/" + expired.size() + " file(s).");
			return succeeded.size();
		}

		CodeSyncLogger.logInfo("moveExpiredFiles: WinSCP not enabled.");
		return 0;
	}

	// ---- Private helpers ----

	// ---- Archive helper ----
	private void archiveFile(CodeSyncSharedFile f) {
		try {
			Path source = Paths.get(f.getStoredPath());
			if (!Files.exists(source)) {
				CodeSyncLogger.logInfo("archiveFile: source not found, skipping: " + source);
				return;
			}
			moveToLocalArchive(f, source);
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "archiveFile Exception", e);
		}
	}

	private void moveToLocalArchive(CodeSyncSharedFile f, Path source) {
		try {
			Path archiveDir = Paths.get(archiveDirectory, f.getShareKey());
			Files.createDirectories(archiveDir);

			Path destination = archiveDir.resolve(source.getFileName());
			if (Files.exists(destination)) {
				String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
				destination = archiveDir.resolve("T_" + timestamp + "___" + source.getFileName());
			}

			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
			f.setStoredPath(destination.toString());
			CodeSyncLogger.logInfo("archiveFile: moved to local archive: " + destination);

		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "archiveFile local fallback Exception", e);
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

	// ---- Validate WinSCP exe exists on disk ----
	private boolean isWinScpAvailable() {
		Path exe = Paths.get(winScpExePath);
		boolean exists = Files.exists(exe);
		if (!exists)
			CodeSyncLogger.logInfo("WinSCP exe not found: " + exe.toAbsolutePath());
		return exists;
	}

	// ---- Test SFTP connectivity with a minimal script ----
	private boolean isServerReachable() {
		Path scriptFile = null;
		try {
			String script = String.join("\n", "option batch on", "option confirm off",
					"open sftp://" + winScpUser + "@" + winScpHost + "/ -password=\"" + winScpPassword + "\"", "pwd",
					"exit");
			scriptFile = Files.createTempFile("winscp_ping_", ".txt");
			Files.writeString(scriptFile, script);

			ProcessBuilder pb = new ProcessBuilder(winScpExePath, "/script=" + scriptFile.toAbsolutePath());
			pb.redirectErrorStream(true);
			Process process = pb.start();
			String output = new String(process.getInputStream().readAllBytes());
			boolean finished = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);

			if (!finished) {
				process.destroyForcibly();
				CodeSyncLogger.logInfo("isServerReachable: timed out.");
				return false;
			}

			int exitCode = process.exitValue();
			boolean reachable = exitCode == 0
					&& (output.contains("Session started") || output.contains("Active session"));
			CodeSyncLogger.logInfo("isServerReachable: exitCode=" + exitCode + " reachable=" + reachable);
			return reachable;

		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "isServerReachable", e);
			return false;
		} finally {
			if (scriptFile != null)
				try {
					Files.deleteIfExists(scriptFile);
				} catch (Exception ignored) {
				}
		}
	}

	// ---- Transfer ALL files in one WinSCP session ----
	private List<CodeSyncSharedFile> transferAllViaWinScp(List<CodeSyncSharedFile> files) {
		Path scriptFile = null;
		List<CodeSyncSharedFile> succeeded = new java.util.ArrayList<>();

		// Build (remoteDir → [file]) map so we mkdir each unique dir only once
		Map<String, List<CodeSyncSharedFile>> byDir = files.stream()
				.filter(f -> Files.exists(Paths.get(f.getStoredPath())))
				.collect(Collectors.groupingBy(f -> winScpRemoteBasePath + "/" + f.getShareKey()));

		if (byDir.isEmpty()) {
			CodeSyncLogger.logInfo("transferAllViaWinScp: no source files exist on disk.");
			return succeeded;
		}

		try {
			// Precompute remote paths so we can match them after transfer
			Map<String, String> fileIdToRemotePath = new LinkedHashMap<>();
			StringBuilder sb = new StringBuilder();
			sb.append("option batch on\n");
			sb.append("option confirm off\n");
			sb.append("open sftp://").append(winScpUser).append("@").append(winScpHost).append("/ -password=\"")
					.append(winScpPassword).append("\"\n");

			for (Map.Entry<String, List<CodeSyncSharedFile>> entry : byDir.entrySet()) {
				String remoteDir = entry.getKey();
				sb.append("call mkdir -p \"").append(remoteDir).append("\"\n");

				for (CodeSyncSharedFile f : entry.getValue()) {
					Path source = Paths.get(f.getStoredPath());
					String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
					String remotePath = remoteDir + "/T_" + timestamp + "___" + source.getFileName().toString();
					fileIdToRemotePath.put(f.getFileId(), remotePath);
					sb.append("put \"").append(source.toAbsolutePath()).append("\" \"").append(remotePath)
							.append("\"\n");
				}
			}
			sb.append("exit\n");

			String script = sb.toString();
			CodeSyncLogger.logInfo("transferAllViaWinScp: script=\n" + script);

			scriptFile = Files.createTempFile("winscp_batch_", ".txt");
			Files.writeString(scriptFile, script);

			ProcessBuilder pb = new ProcessBuilder(winScpExePath, "/script=" + scriptFile.toAbsolutePath());
			pb.redirectErrorStream(true);
			Process process = pb.start();
			String output = new String(process.getInputStream().readAllBytes());
			boolean finished = process.waitFor(600, java.util.concurrent.TimeUnit.SECONDS); // 10 min for batch

			if (!finished) {
				process.destroyForcibly();
				CodeSyncLogger.logInfo("transferAllViaWinScp: timed out after 300s.\n" + output);
				return succeeded;
			}

			int exitCode = process.exitValue();
			CodeSyncLogger.logInfo("transferAllViaWinScp: exitCode=" + exitCode + "\n" + output);

			// Parse output — each successful transfer prints "filename | size | speed |
			// 100%"
			// Mark individual files as succeeded by checking if their filename appears with
			// 100%
			for (CodeSyncSharedFile f : files) {
				Path source = Paths.get(f.getStoredPath());
				String fileName = source.getFileName().toString();
				String remotePath = fileIdToRemotePath.get(f.getFileId());

				if (remotePath == null) {
					CodeSyncLogger.logInfo("transferAllViaWinScp: skipped (no source): " + fileName);
					continue;
				}

				// Check output contains the filename with 100% completion
				boolean thisFileTransferred = output.contains(fileName) && output.contains("100%");

				if (thisFileTransferred) {
					try {
						Files.deleteIfExists(source);
					} catch (Exception ex) {
						CodeSyncLogger.logError(getClass(), "delete after transfer: " + fileName, ex);
					}
					f.setStoredPath(remotePath);
					f.setIsFileMoved(true);
					succeeded.add(f);
					CodeSyncLogger.logInfo("transferAllViaWinScp: ✅ moved: " + fileName + " → " + remotePath);
				} else {
					CodeSyncLogger.logInfo("transferAllViaWinScp: ❌ not confirmed in output: " + fileName);
				}
			}

		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "transferAllViaWinScp", e);
		} finally {
			if (scriptFile != null)
				try {
					Files.deleteIfExists(scriptFile);
				} catch (Exception ignored) {
				}
		}

		return succeeded;
	}

}
