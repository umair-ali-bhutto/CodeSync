package com.ag.config;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.*;

@Configuration
public class SecurityProtectionConfig {

	@Value("${security.blocked-ips:}")
	private String blockedIpsProp;

	@Value("${security.rate.limit.capacity}")
	private int capacity;

	@Value("${security.rate.limit.refill.seconds}")
	private int refillSeconds;

	@Value("${security.rate.limit.to.refill}")
	private int refillAmount;

	private final Set<String> blockedIps = new HashSet<>();
	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	@PostConstruct
	public void init() {
		if (!blockedIpsProp.trim().isEmpty()) {
			blockedIps.addAll(Arrays.asList(blockedIpsProp.split(",")));
		}
	}

	public boolean isBlocked(String ip) {
		return blockedIps.contains(ip);
	}

	public Bucket resolveBucket(String ip) {
		return buckets.computeIfAbsent(ip, k -> newBucket());
	}

	private Bucket newBucket() {
		Refill refill = Refill.intervally(refillAmount, Duration.ofSeconds(refillSeconds));
		Bandwidth limit = Bandwidth.classic(capacity, refill);
		return Bucket4j.builder().addLimit(limit).build();
	}
}