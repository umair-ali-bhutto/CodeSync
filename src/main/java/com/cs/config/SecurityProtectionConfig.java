package com.cs.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;

/**
 * Configuration class responsible for application-level security protections
 * such as:
 * <ul>
 * <li>IP blocking</li>
 * <li>Per-IP rate limiting using Bucket4j</li>
 * </ul>
 * <p>
 * This helps protect public endpoints from abuse, brute force access, and
 * denial-of-service attempts.
 */
@Configuration
public class SecurityProtectionConfig {

	/**
	 * Comma-separated list of blocked IP addresses loaded from application
	 * properties.
	 */
	@Value("${security.blocked-ips:}")
	private String blockedIpsProp;

	/**
	 * Maximum number of requests allowed per IP within the configured time window.
	 */
	@Value("${security.rate.limit.capacity}")
	private int capacity;

	/**
	 * Time interval (in seconds) after which tokens are refilled in the rate
	 * limiting bucket.
	 */
	@Value("${security.rate.limit.refill.seconds}")
	private int refillSeconds;

	/**
	 * Number of tokens to refill after each interval.
	 */
	@Value("${security.rate.limit.to.refill}")
	private int refillAmount;

	/**
	 * In-memory set containing all blocked IP addresses.
	 */
	private final Set<String> blockedIps = new HashSet<>();

	/**
	 * Map of IP addresses to their corresponding Bucket4j rate limit bucket.
	 * <p>
	 * Each IP gets its own bucket to track request consumption independently.
	 */
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	/**
	 * Initializes the blocked IP list from application properties after the Spring
	 * context is created.
	 */
	@PostConstruct
	public void init() {
		if (!blockedIpsProp.trim().isEmpty()) {
			blockedIps.addAll(Arrays.asList(blockedIpsProp.split(",")));
		}
	}

	/**
	 * Checks whether the provided IP address is present in the blocked IP list.
	 *
	 * @param ip client IP address
	 * @return true if the IP is blocked, false otherwise
	 */
	public boolean isBlocked(String ip) {
		return blockedIps.contains(ip);
	}

	/**
	 * Resolves (or creates if absent) a rate limiting bucket for the provided IP
	 * address.
	 *
	 * @param ip client IP address
	 * @return Bucket associated with the IP
	 */
	public Bucket resolveBucket(String ip) {
		return buckets.computeIfAbsent(ip, k -> newBucket());
	}

	/**
	 * Creates a new Bucket4j rate limiting bucket using the configured capacity and
	 * refill strategy.
	 *
	 * @return newly configured Bucket instance
	 */
	private Bucket newBucket() {
		Refill refill = Refill.intervally(refillAmount, Duration.ofSeconds(refillSeconds));
		Bandwidth limit = Bandwidth.classic(capacity, refill);
		return Bucket4j.builder().addLimit(limit).build();
	}
}