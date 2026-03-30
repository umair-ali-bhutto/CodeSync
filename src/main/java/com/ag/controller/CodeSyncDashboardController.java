package com.ag.controller;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.ag.config.CodeSyncLogger;
import com.ag.dto.DashboardSummary;
import com.ag.entity.CodeSyncAudit;
import com.ag.service.CodeSyncDashboardService;

@Controller
@RequestMapping("/admin/dashboard")
public class CodeSyncDashboardController {

	private static final String ALLOWED_IP = "172.191.1.223"; // your allowed machine

	private final CodeSyncDashboardService dashboardService;

	public CodeSyncDashboardController(CodeSyncDashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping
	public String dashboard(HttpServletRequest request, Model model, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		CodeSyncLogger.logInfo(
				"Dashboard opened from IP = " + request.getRemoteAddr() + ", page = " + page + ", size = " + size);

		validateIp(request);

		DashboardSummary summary = dashboardService.getSummary();
		model.addAttribute("summary", summary);
		model.addAttribute("todayAudits", dashboardService.getTodayAudits(page, size));
		model.addAttribute("topClients", dashboardService.getTopClientsToday());
		model.addAttribute("currentPage", page);

		return "dashboard";
	}

	@GetMapping("/download")
	public void downloadCsv(HttpServletRequest request, HttpServletResponse response) throws Exception {

		CodeSyncLogger.logInfo("CSV download triggered by IP = " + request.getRemoteAddr());
		validateIp(request);

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
		String timestamp = LocalDateTime.now().format(formatter);

		response.setContentType("text/csv");
		response.setHeader("Content-Disposition", "attachment; filename=CodeSync-Audits-" + timestamp + ".csv");

		List<CodeSyncAudit> audits = dashboardService.getAllTodayAudits();
		PrintWriter writer = response.getWriter();

		// CSV header - exactly matches entity fields
		writer.println("\"ID\",\"HTTP_METHOD\",\"URI\",\"QUERY_STRING\",\"CLIENT_IP\",\"STATUS_CODE\",\"CONTENT_SIZE\","
				+ "\"REQUEST_BODY\",\"DURATION_MS\",\"CREATED_AT\",\"FORWARDED_FOR\",\"REAL_IP\",\"USER_AGENT\","
				+ "\"BROWSER_INFO\",\"LANGUAGE\",\"REFERER\",\"ORIGIN\",\"HOST\",\"SEC_FETCH_SITE_MODE_DEST\","
				+ "\"SEC_CH_UA_PLATFORM_MOBILE\"");

		for (CodeSyncAudit a : audits) {
			writer.printf(
					"\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
					safe(a.getId()), safe(a.getHttpMethod()), safe(a.getUri()), safe(a.getQueryString()),
					safe(a.getClientIp()), safe(a.getStatusCode()), safe(a.getContentSize()), safe(a.getRequestBody()),
					safe(a.getDurationMs()), safe(a.getCreatedAt()), safe(a.getForwardedFor()), safe(a.getRealIp()),
					safe(a.getUserAgent()), safe(a.getBrowserInfo()), safe(a.getLanguage()), safe(a.getReferer()),
					safe(a.getOrigin()), safe(a.getHost()), safe(a.getSecFetchSiteModeDest()),
					safe(a.getSecChUaPlatformMobile()));
		}
		writer.flush();

		CodeSyncLogger.logInfo("CSV generated successfully with " + audits.size() + " records");
	}

	// Helper to safely convert nulls and escape quotes in CSV
	private String safe(Object obj) {
		if (obj == null)
			return "";
		String str = obj.toString();
		// Escape any double quotes in the field
		str = str.replace("\"", "\"\"");
		return str;
	}

	private void validateIp(HttpServletRequest request) {
		String ip = request.getRemoteAddr();
		if (!ALLOWED_IP.equals(ip)) {
			CodeSyncLogger.logInfo("Unauthorized dashboard access attempt from " + ip);
			throw new RuntimeException("Access denied for IP: " + ip);
		}
	}

	private String safe(String value) {
		return value == null ? "" : value.replace(",", " ");
	}
}