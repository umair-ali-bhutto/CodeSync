package com.cs.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "CODE_SYNC_CLIENTS")
public class CodeSyncClient {

	@Id
	@Column(name = "ip", nullable = false, length = 45)
	private String ip;

	@Column(name = "name")
	private String name;

	@Column(name = "inserted_on")
	private Timestamp insertedOn;

	@Column(name = "updated_on")
	private Timestamp updatedOn;

	@Column(name = "updated_by", length = 45)
	private String updatedBy;

	// getters setters

	@PrePersist
	public void onCreate() {
		insertedOn = new Timestamp(System.currentTimeMillis());
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Timestamp getInsertedOn() {
		return insertedOn;
	}

	public void setInsertedOn(Timestamp insertedOn) {
		this.insertedOn = insertedOn;
	}

	public Timestamp getUpdatedOn() {
		return updatedOn;
	}

	public void setUpdatedOn(Timestamp updatedOn) {
		this.updatedOn = updatedOn;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public void setUpdatedBy(String updatedBy) {
		this.updatedBy = updatedBy;
	}
}
