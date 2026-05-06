package com.cs.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint;
import org.springframework.boot.micrometer.metrics.actuate.endpoint.MetricsEndpoint.MetricNamesDescriptor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/dashboard")
public class ActuatorAdminController {

	private final HealthEndpoint healthEndpoint;
	private final MetricsEndpoint metricsEndpoint;

	public ActuatorAdminController(HealthEndpoint healthEndpoint, MetricsEndpoint metricsEndpoint) {
		this.healthEndpoint = healthEndpoint;
		this.metricsEndpoint = metricsEndpoint;
	}

	@GetMapping("/status")
	public String getFullDashboard(Model model) {

		// 1. Health
		var health = healthEndpoint.health();
		String status = health.getStatus().getCode();
		model.addAttribute("overallHealth", status);
		model.addAttribute("healthUp", "UP".equals(status));

		// 2. Categorized metrics with units
		Map<String, Map<String, String>> categories = new LinkedHashMap<>();
		categories.put("JVM Memory", new TreeMap<>());
		categories.put("JVM Threads", new TreeMap<>());
		categories.put("System", new TreeMap<>());
		categories.put("HTTP & Web", new TreeMap<>());
		categories.put("Other", new TreeMap<>());

		int totalMetrics = 0;
		MetricNamesDescriptor names = metricsEndpoint.listNames();

		for (String name : names.getNames()) {
			try {
				var metric = metricsEndpoint.metric(name, null);
				if (metric == null || metric.getMeasurements().isEmpty())
					continue;

				double value = metric.getMeasurements().get(0).getValue();
				String formatted = formatMetric(name, value);
				totalMetrics++;

				if (name.startsWith("jvm.memory") || name.startsWith("jvm.gc") || name.contains("heap")) {
					categories.get("JVM Memory").put(name, formatted);
				} else if (name.startsWith("jvm.thread") || name.startsWith("jvm.class")) {
					categories.get("JVM Threads").put(name, formatted);
				} else if (name.startsWith("jvm")) {
					categories.get("JVM Threads").put(name, formatted);
				} else if (name.startsWith("system") || name.startsWith("process") || name.startsWith("disk")) {
					categories.get("System").put(name, formatted);
				} else if (name.startsWith("http") || name.startsWith("tomcat")) {
					categories.get("HTTP & Web").put(name, formatted);
				} else {
					categories.get("Other").put(name, formatted);
				}
			} catch (Exception ignored) {
			}
		}

		// Remove empty categories
		categories.entrySet().removeIf(e -> e.getValue().isEmpty());

		model.addAttribute("categories", categories);
		model.addAttribute("totalMetrics", totalMetrics);
		model.addAttribute("totalCategories", categories.size());
		model.addAttribute("refreshTime", java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss")));

		return "status";
	}

	private String formatMetric(String name, double value) {
		if (name.contains("bytes") || name.contains("memory") || name.contains("size")) {
			if (value >= 1_073_741_824)
				return String.format("%.2f GB", value / 1_073_741_824);
			if (value >= 1_048_576)
				return String.format("%.2f MB", value / 1_048_576);
			if (value >= 1024)
				return String.format("%.2f KB", value / 1024);
			return String.format("%.0f B", value);
		} else if (name.contains("usage") || name.contains("percent")) {
			return String.format("%.1f%%", value * 100);
		} else if (name.contains("time") || name.contains("duration")) {
			if (value >= 60)
				return String.format("%.1f min", value / 60);
			if (value >= 1)
				return String.format("%.2f s", value);
			return String.format("%.1f ms", value * 1000);
		} else if (value >= 1_000_000) {
			return String.format("%.2fM", value / 1_000_000);
		} else if (value >= 1_000) {
			return String.format("%.1fK", value / 1_000);
		}
		return String.format("%.0f", value);
	}
}