package com.ag.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.ag.config.CodeSyncLogger;
import com.ag.dto.DashboardSummary;
import com.ag.dto.TopClientDto;
import com.ag.entity.CodeSyncAudit;
import com.ag.repository.CodeSyncAuditRepository;

@Service
public class CodeSyncDashboardService {

	private final CodeSyncAuditRepository auditRepository;

	public CodeSyncDashboardService(CodeSyncAuditRepository auditRepository) {
		this.auditRepository = auditRepository;
	}

	public DashboardSummary getSummary() {
		Timestamp todayStart = ts(LocalDate.now());
		Timestamp tomorrowStart = ts(LocalDate.now().plusDays(1));
		Timestamp yesterdayStart = ts(LocalDate.now().minusDays(1));

		long today = auditRepository.countBetween(todayStart, tomorrowStart);
		long yesterday = auditRepository.countBetween(yesterdayStart, todayStart);
		long activeToday = auditRepository.activeClientsBetween(todayStart, tomorrowStart);
		long activeYesterday = auditRepository.activeClientsBetween(yesterdayStart, todayStart);

		CodeSyncLogger.logInfo("Dashboard summary loaded: today = " + today + ", yesterday = " + yesterday
				+ ", activeToday = " + activeToday + ", activeYesterday = " + activeYesterday);

		return new DashboardSummary(today, yesterday, activeToday, activeYesterday);
	}

	public List<CodeSyncAudit> getTodayAudits(int page, int size) {
		Timestamp start = ts(LocalDate.now());
		Timestamp end = ts(LocalDate.now().plusDays(1));

		CodeSyncLogger.logInfo("Loading paginated audits page = " + page + ", size = " + size);

		return auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, PageRequest.of(page, size))
				.getContent();
	}

	public List<CodeSyncAudit> getAllTodayAudits() {
		Timestamp start = ts(LocalDate.now());
		Timestamp end = ts(LocalDate.now().plusDays(1));
		return auditRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
	}

	public List<TopClientDto> getTopClientsToday() {
		Timestamp start = ts(LocalDate.now());
		Timestamp end = ts(LocalDate.now().plusDays(1));
		return auditRepository.topClients(start, end);
	}

	private Timestamp ts(LocalDate date) {
		return Timestamp.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}
}