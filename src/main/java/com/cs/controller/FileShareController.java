package com.cs.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

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
import com.cs.config.CodeSyncUtil;
import com.cs.dto.SharedFileDTO;
import com.cs.entity.CodeSyncSharedFile;
import com.cs.service.CodeSyncSharedFileService;

/**
 * REST API for file sharing within a CodeSync share key.
 *
 * Base path: /api/files/{key}
 *
 * POST /api/files/{key}/upload — upload a single file GET /api/files/{key}/list
 * — list all files for the key (JSON) GET /api/files/{key}/download/{id} —
 * download a file by its fileId DELETE /api/files/{key}/delete/{id} — delete a
 * file by its fileId
 */
@RestController
@RequestMapping("/api/files")
public class FileShareController {

	private final CodeSyncSharedFileService fileService;

	public FileShareController(CodeSyncSharedFileService fileService) {
		this.fileService = fileService;
	}

	/**
	 * Upload a file. Returns 201 on success, 413 if file is too large.
	 */
	@PostMapping("/{key}/upload")
	public ResponseEntity<String> upload(@PathVariable String key, @RequestParam("file") MultipartFile file) {

		CodeSyncUtil.validateKey(key);

		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body("No file provided.");
		}

		try {
			CodeSyncSharedFile saved = fileService.store(key, file);
			return ResponseEntity.status(HttpStatus.CREATED).body(saved.getFileId());
		} catch (IllegalArgumentException e) {
			// File too large
			return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(e.getMessage());
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed.");
		}
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
	public ResponseEntity<Void> delete(@PathVariable String key, @PathVariable String fileId) {

		CodeSyncUtil.validateKey(key);

		try {
			CodeSyncSharedFile f = fileService.findByFileId(fileId);
			if (!f.getShareKey().equals(key)) {
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}
			fileService.delete(fileId);
			return ResponseEntity.ok().build();
		} catch (IllegalArgumentException e) {
			return ResponseEntity.notFound().build();
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Exception", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
	}
}
