package com.ag.entity;

import java.sql.Timestamp;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.PrePersist;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "CODE_SYNC_AUDIT")
public class CodeSyncAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CODE_SYNC_AUDIT_SEQ")
	@SequenceGenerator(name = "CODE_SYNC_AUDIT_SEQ", sequenceName = "CODE_SYNC_AUDIT_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "HTTP_METHOD", length = 10)
	private String httpMethod;

	@Column(name = "URI", length = 500)
	private String uri;

	@Column(name = "QUERY_STRING", length = 1000)
	private String queryString;

	@Column(name = "CLIENT_IP", length = 45)
	private String clientIp;

	@Column(name = "STATUS_CODE")
	private int statusCode;

	@Lob
	@Column(name = "REQUEST_BODY")
	private String requestBody;

	@Column(name = "DURATION_MS")
	private long durationMs;

	@Column(name = "CREATED_AT", updatable = false)
	private Timestamp createdAt;

	@Column(name = "FORWARDED_FOR")
	private String forwardedFor;

	@Column(name = "REAL_IP")
	private String realIp;

	@PrePersist
	public void onCreate() {
		createdAt = new Timestamp(System.currentTimeMillis());
	}

	// getters & setters

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	public void setHttpMethod(String httpMethod) {
		this.httpMethod = httpMethod;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getQueryString() {
		return queryString;
	}

	public void setQueryString(String queryString) {
		this.queryString = queryString;
	}

	public String getClientIp() {
		return clientIp;
	}

	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public void setStatusCode(int statusCode) {
		this.statusCode = statusCode;
	}

	public String getRequestBody() {
		return requestBody;
	}

	public void setRequestBody(String requestBody) {
		this.requestBody = requestBody;
	}

	public long getDurationMs() {
		return durationMs;
	}

	public void setDurationMs(long durationMs) {
		this.durationMs = durationMs;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Timestamp createdAt) {
		this.createdAt = createdAt;
	}

	public String getForwardedFor() {
		return forwardedFor;
	}

	public void setForwardedFor(String forwardedFor) {
		this.forwardedFor = forwardedFor;
	}

	public String getRealIp() {
		return realIp;
	}

	public void setRealIp(String realIp) {
		this.realIp = realIp;
	}

}