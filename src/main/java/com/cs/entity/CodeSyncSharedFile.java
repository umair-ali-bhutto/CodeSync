package com.cs.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Represents a file uploaded against a share key.
 */
@Entity
@Table(name = "CODE_SYNC_SHARED_FILE", indexes = {
		@Index(name = "IDX_SF_SHARE_ACTIVE_UPLOAD", columnList = "SHARE_KEY, IS_ACTIVE, UPLOADED_AT"),
		@Index(name = "IDX_SF_ACTIVE_EXPIRES", columnList = "IS_ACTIVE, EXPIRES_AT") })
public class CodeSyncSharedFile {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CODE_SYNC_SHARED_FILE_SEQ")
	@SequenceGenerator(name = "CODE_SYNC_SHARED_FILE_SEQ", sequenceName = "CODE_SYNC_SHARED_FILE_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "SHARE_KEY", nullable = false, length = 100)
	private String shareKey;

	@Column(name = "FILE_ID", nullable = false, unique = true, length = 64)
	private String fileId;

	@Column(name = "ORIGINAL_NAME", nullable = false, length = 512)
	private String originalName;

	@Column(name = "CONTENT_TYPE", length = 128)
	private String contentType;

	@Column(name = "FILE_SIZE")
	private Long fileSize;

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

	@Column(name = "UPLOADER_IP", length = 64)
	private String uploaderIp;

	@Column(name = "UPLOADER_NAME", length = 128)
	private String uploaderName;

	@Column(name = "EXPIRES_AT")
	private Timestamp expiresAt;

	@Column(name = "IS_FILE_MOVED", nullable = false)
	private Boolean isFileMoved = false;

	@Column(name = "IS_FILE_DELETED_DIRECTLY", nullable = false)
	private Boolean isFileDeletedDirectly = false;

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

	public String getUploaderIp() {
		return uploaderIp;
	}

	public void setUploaderIp(String uploaderIp) {
		this.uploaderIp = uploaderIp;
	}

	public String getUploaderName() {
		return uploaderName;
	}

	public void setUploaderName(String uploaderName) {
		this.uploaderName = uploaderName;
	}

	public Timestamp getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Timestamp expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Boolean getIsFileMoved() {
		return isFileMoved;
	}

	public void setIsFileMoved(Boolean isFileMoved) {
		this.isFileMoved = isFileMoved;
	}

	public Boolean getIsFileDeletedDirectly() {
		return isFileDeletedDirectly;
	}

	public void setIsFileDeletedDirectly(Boolean isFileDeletedDirectly) {
		this.isFileDeletedDirectly = isFileDeletedDirectly;
	}
}
