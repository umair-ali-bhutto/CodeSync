package com.cs.dto;

import java.sql.Timestamp;

/**
 * Lightweight DTO returned to the frontend for each shared file.
 */
public class SharedFileDTO {

	private String fileId;
	private String originalName;
	private String contentType;
	private Long fileSize;
	private Timestamp uploadedAt;
	private Long downloadCount;
	private Timestamp lastDownloadedAt;

	public SharedFileDTO() {
	}

	public SharedFileDTO(String fileId, String originalName, String contentType, Long fileSize, Timestamp uploadedAt,
			Long downloadCount, Timestamp lastDownloadedAt) {
		this.fileId = fileId;
		this.originalName = originalName;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.uploadedAt = uploadedAt;
		this.downloadCount = downloadCount;
		this.lastDownloadedAt = lastDownloadedAt;
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

	public Timestamp getLastDownloadedAt() {
		return lastDownloadedAt;
	}

	public void setLastDownloadedAt(Timestamp lastDownloadedAt) {
		this.lastDownloadedAt = lastDownloadedAt;
	}

}
