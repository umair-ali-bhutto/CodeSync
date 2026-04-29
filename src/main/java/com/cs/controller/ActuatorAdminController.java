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
		// 1. App Health Status
		model.addAttribute("overallHealth", healthEndpoint.health().getStatus().getCode());

		// 2. Organized Metrics Map
		Map<String, Map<String, String>> categorizedMetrics = new LinkedHashMap<>();
		categorizedMetrics.put("System", new TreeMap<>());
		categorizedMetrics.put("JVM", new TreeMap<>());
		categorizedMetrics.put("HTTP & Web", new TreeMap<>());
		categorizedMetrics.put("Other", new TreeMap<>());

		MetricNamesDescriptor names = metricsEndpoint.listNames();

		for (String name : names.getNames()) {
			try {
				var metric = metricsEndpoint.metric(name, null);
				if (metric != null && !metric.getMeasurements().isEmpty()) {
					double value = metric.getMeasurements().get(0).getValue();
					String formattedValue = formatMetric(name, value);

					// Categorization Logic
					if (name.startsWith("system") || name.startsWith("process") || name.startsWith("disk")) {
						categorizedMetrics.get("System").put(name, formattedValue);
					} else if (name.startsWith("jvm")) {
						categorizedMetrics.get("JVM").put(name, formattedValue);
					} else if (name.startsWith("http") || name.startsWith("tomcat")) {
						categorizedMetrics.get("HTTP & Web").put(name, formattedValue);
					} else {
						categorizedMetrics.get("Other").put(name, formattedValue);
					}
				}
			} catch (Exception ignored) {
			}
		}

		model.addAttribute("categories", categorizedMetrics);
		return "status";
	}

	private String formatMetric(String name, double value) {
		if (name.contains("bytes") || name.contains("memory") || name.contains("size")) {
			return String.format("%.2f MB", value / (1024 * 1024));
		} else if (name.contains("usage") || name.contains("percent")) {
			return String.format("%.2f %%", value * 100);
		} else if (name.contains("time")) {
			return String.format("%.4f s", value);
		}
		return String.format("%.0f", value);
	}
}