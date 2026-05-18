package com.cs.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cs.config.CodeSyncLogger;
import com.cs.dto.SharedFileDTO;
import com.cs.entity.CodeSyncSharedFile;
import com.cs.service.CodeSyncClientCache;
import com.cs.service.CodeSyncSharedFileService;
import com.cs.util.CodeSyncUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST API for file sharing within a CodeSync share key.
 */
@RestController
@RequestMapping("/api/files")
public class FileShareController {

	@Value("${codesync.max-total-files}")
	private long maxTotalFiles;

	private final CodeSyncSharedFileService fileService;

	public FileShareController(CodeSyncSharedFileService fileService) {
		this.fileService = fileService;
	}

	/**
	 * Upload a file. Returns 201 on success, 413 if file is too large, 409 if file
	 * limit reached.
	 */
	@PostMapping("/{key}/upload")
	public ResponseEntity<String> upload(@PathVariable String key, @RequestParam("file") MultipartFile file,
			HttpServletRequest request) {

		CodeSyncUtil.validateKey(key);

		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body("No file provided.");
		}

		try {
			String ip = CodeSyncUtil.getClientIp(request);
			String clientName = CodeSyncClientCache.getNameByIp(ip);

			// Enforce active file limit
			long active = fileService.countActiveFiles(key);
			if (active >= maxTotalFiles) {
				return ResponseEntity.status(HttpStatus.CONFLICT)
						.body("File limit reached. Maximum " + maxTotalFiles + " active files per share.");
			}

			CodeSyncSharedFile saved = fileService.store(key, file, ip, clientName);
			request.setAttribute("uploadedFileSize", file.getSize());
			request.setAttribute("uploadedFileName", file.getOriginalFilename());

			return ResponseEntity.status(HttpStatus.CREATED).body(saved.getFileId());
		} catch (IllegalArgumentException e) {
			// File too large
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(e.getMessage());
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed.");
		}
	}

	/**
	 * Gets count of Active files
	 */
	@GetMapping("/{key}/count")
	public ResponseEntity<Long> count(@PathVariable String key) {
		CodeSyncUtil.validateKey(key);
		return ResponseEntity.ok(fileService.countActiveFiles(key));
	}

	/**
	 * List files for the share key. Returns JSON array of SharedFileDTO.
	 */
	@GetMapping("/{key}/list")
	public ResponseEntity<List<SharedFileDTO>> list(@PathVariable String key) {
		CodeSyncUtil.validateKey(key);
		List<SharedFileDTO> files = fileService.listFiles(key);
		return ResponseEntity.ok(files);
	}

	/**
	 * Download a file by its fileId. Streams the file with appropriate headers.
	 */
	@GetMapping("/{key}/download/{fileId}")
	public ResponseEntity<Resource> download(@PathVariable String key, @PathVariable String fileId) {

		CodeSyncUtil.validateKey(key);

		try {
			CodeSyncSharedFile f = fileService.findByFileId(fileId);

			// Security: make sure the file belongs to this key
			if (!f.getShareKey().equals(key)) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			// Stop expired files from being downloaded
			if (f.getExpiresAt() != null
					&& f.getExpiresAt().before(new java.sql.Timestamp(System.currentTimeMillis()))) {
				return ResponseEntity.status(HttpStatus.GONE).body(null); // 410 Gone
			}

			// Stop inactive (soft-deleted) files from being downloaded
			if (!Boolean.TRUE.equals(f.getIsActive())) {
				return ResponseEntity.status(HttpStatus.GONE).body(null);
			}

			fileService.incrementDownload(f.getFileId());

			Resource resource = new FileSystemResource(Paths.get(f.getStoredPath()));
			if (!resource.exists()) {
				return ResponseEntity.notFound().build();
			}

			String contentType = f.getContentType();
			if (contentType == null || contentType.isBlank()) {
				try {
					contentType = Files.probeContentType(Paths.get(f.getStoredPath()));
				} catch (IOException ex) {
					contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
				}
			}

			return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getOriginalName() + "\"")
					.contentLength(f.getFileSize()).body(resource);

		} catch (IllegalArgumentException e) {
			return ResponseEntity.notFound().build();
		}
	}

	/**
	 * Delete a file by its fileId.
	 */
	@DeleteMapping("/{key}/delete/{fileId}")
	public ResponseEntity<String> delete(@PathVariable String key, @PathVariable String fileId) {

		CodeSyncUtil.validateKey(key);

		try {
			CodeSyncSharedFile f = fileService.findByFileId(fileId);
			if (!f.getShareKey().equals(key)) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
			}
			fileService.delete(fileId);
			return ResponseEntity.ok("Deleted");
		} catch (IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Exception", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Delete failed");
		}
	}

	/**
	 * DELETE ALL active files for a share key
	 */
	@DeleteMapping("/{key}/delete-all")
	public ResponseEntity<String> deleteAll(@PathVariable String key) {
		CodeSyncUtil.validateKey(key);
		try {
			int count = fileService.deleteAll(key);
			return ResponseEntity.ok("Deleted " + count + " files.");
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Delete all failed.");
		}
	}

}
