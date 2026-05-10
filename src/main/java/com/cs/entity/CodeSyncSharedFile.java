package com.cs.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Represents a file uploaded against a share key.
 */
@Entity
@Table(name = "CODE_SYNC_SHARED_FILE")
public class CodeSyncSharedFile {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CODE_SYNC_SHARED_FILE_SEQ")
	@SequenceGenerator(name = "CODE_SYNC_SHARED_FILE_SEQ", sequenceName = "CODE_SYNC_SHARED_FILE_SEQ", allocationSize = 1)
	private Long id;

	/** The share key this file belongs to (matches CodeSync.shareKey). */
	@Column(name = "SHARE_KEY", nullable = false, length = 100)
	private String shareKey;

	/** UUID used in the filesystem path and download URL. */
	@Column(name = "FILE_ID", nullable = false, unique = true, length = 64)
	private String fileId;

	/** Original filename as uploaded by the user. */
	@Column(name = "ORIGINAL_NAME", nullable = false, length = 512)
	private String originalName;

	/** MIME type detected or provided at upload time. */
	@Column(name = "CONTENT_TYPE", length = 128)
	private String contentType;

	/** File size in bytes. */
	@Column(name = "FILE_SIZE")
	private Long fileSize;

	/** Path on disk relative to the configured upload root. */
	@Column(name = "STORED_PATH", nullable = false, length = 1024)
	private String storedPath;

	@Column(name = "UPLOADED_AT", updatable = false)
	private Timestamp uploadedAt;

	@Column(name = "DOWNLOAD_COUNT", nullable = false)
	private Long downloadCount = 0L;

	@Column(name = "IS_ACTIVE", nullable = false)
	private Boolean isActive = true;

	@Column(name = "DELETED_AT")
	private Timestamp deletedAt;

	@Column(name = "LAST_DOWNLOADED_AT")
	private Timestamp lastDownloadedAt;

	@PrePersist
	public void onCreate() {
		uploadedAt = new Timestamp(System.currentTimeMillis());
	}

	// ---- Getters & Setters ----

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getShareKey() {
		return shareKey;
	}

	public void setShareKey(String shareKey) {
		this.shareKey = shareKey;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getOriginalName() {
		return originalName;
	}

	public void setOriginalName(String originalName) {
		this.originalName = originalName;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public String getStoredPath() {
		return storedPath;
	}

	public void setStoredPath(String storedPath) {
		this.storedPath = storedPath;
	}

	public Timestamp getUploadedAt() {
		return uploadedAt;
	}

	public void setUploadedAt(Timestamp uploadedAt) {
		this.uploadedAt = uploadedAt;
	}

	public Long getDownloadCount() {
		return downloadCount;
	}

	public void setDownloadCount(Long downloadCount) {
		this.downloadCount = downloadCount;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
	}

	public Timestamp getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Timestamp deletedAt) {
		this.deletedAt = deletedAt;
	}

	public Timestamp getLastDownloadedAt() {
		return lastDownloadedAt;
	}

	public void setLastDownloadedAt(Timestamp lastDownloadedAt) {
		this.lastDownloadedAt = lastDownloadedAt;
	}

}
