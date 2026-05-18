package com.cs.entity;

import java.sql.Timestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "CODE_SYNC_CLIENTS")
public class CodeSyncClient {

	@Id
	private String ip;

	private String name;

	private Timestamp inserted_on;

	// getters setters

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

	public Timestamp getInserted_on() {
		return inserted_on;
	}

	public void setInserted_on(Timestamp inserted_on) {
		this.inserted_on = inserted_on;
	}
}
