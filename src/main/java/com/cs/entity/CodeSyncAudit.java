package com.cs.entity;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "CODE_SYNC_AUDIT")
public class CodeSyncAudit {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "CODE_SYNC_AUDIT_SEQ")
	@SequenceGenerator(name = "CODE_SYNC_AUDIT_SEQ", sequenceName = "CODE_SYNC_AUDIT_SEQ", allocationSize = 1)
	private Long id;

	@Column(name = "HTTP_METHOD")
	private String httpMethod;

	@Column(name = "URI")
	private String uri;

	@Column(name = "QUERY_STRING")
	private String queryString;

	@Column(name = "CLIENT_IP")
	private String clientIp;

	@Column(name = "STATUS_CODE")
	private int statusCode;

	@Column(name = "CONTENT_SIZE")
	private int contentSize;

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

	@Column(name = "USER_AGENT")
	private String userAgent;

	@Column(name = "BROWSER_INFO")
	private String browserInfo;

	@Column(name = "LANGUAGE")
	private String language;

	@Column(name = "REFERER")
	private String referer;

	@Column(name = "ORIGIN")
	private String origin;

	@Column(name = "HOST")
	private String host;

	@Column(name = "SEC_FETCH_SITE_MODE_DEST")
	private String secFetchSiteModeDest;

	@Column(name = "SEC_CH_UA_PLATFORM_MOBILE")
	private String secChUaPlatformMobile;

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

	public int getContentSize() {
		return contentSize;
	}

	public void setContentSize(int contentSize) {
		this.contentSize = contentSize;
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

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getBrowserInfo() {
		return browserInfo;
	}

	public void setBrowserInfo(String browserInfo) {
		this.browserInfo = browserInfo;
	}

	public String getLanguage() {
		return language;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public String getReferer() {
		return referer;
	}

	public void setReferer(String referer) {
		this.referer = referer;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getSecFetchSiteModeDest() {
		return secFetchSiteModeDest;
	}

	public void setSecFetchSiteModeDest(String secFetchSiteModeDest) {
		this.secFetchSiteModeDest = secFetchSiteModeDest;
	}

	public String getSecChUaPlatformMobile() {
		return secChUaPlatformMobile;
	}

	public void setSecChUaPlatformMobile(String secChUaPlatformMobile) {
		this.secChUaPlatformMobile = secChUaPlatformMobile;
	}

}