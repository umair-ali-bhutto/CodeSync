package com.cs.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CODE_SYNC_BLOCKED_IPS")
public class CodeSyncBlockedIp {

	@Id
	@Column(name = "ip", nullable = false, length = 45)
	private String ip;

	@Column(name = "blocked_by", length = 100)
	private String blockedBy;

	@Column(name = "blocked_at")
	private LocalDateTime blockedAt;

	@Column(name = "reason", length = 255)
	private String reason;

	public CodeSyncBlockedIp() {
	}

	public CodeSyncBlockedIp(String ip, String blockedBy, String reason) {
		this.ip = ip;
		this.blockedBy = blockedBy;
		this.reason = reason;
		this.blockedAt = LocalDateTime.now();
	}

	// getters / setters

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getBlockedBy() {
		return blockedBy;
	}

	public void setBlockedBy(String blockedBy) {
		this.blockedBy = blockedBy;
	}

	public LocalDateTime getBlockedAt() {
		return blockedAt;
	}

	public void setBlockedAt(LocalDateTime blockedAt) {
		this.blockedAt = blockedAt;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

}