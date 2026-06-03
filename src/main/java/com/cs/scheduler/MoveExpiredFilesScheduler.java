package com.cs.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cs.config.CodeSyncLogger;
import com.cs.service.CodeSyncSharedFileService;

/**
 * Runs periodically to move expired files. Interval is configurable via
 * application.properties: codesync.file-moving.cron
 */
@Component
public class MoveExpiredFilesScheduler {

	@Value("${codesync.file-moving.cron.enabled:false}")
	private Boolean schedularEnabled;

	private final CodeSyncSharedFileService fileService;

	public MoveExpiredFilesScheduler(CodeSyncSharedFileService fileService) {
		this.fileService = fileService;
	}

	@Scheduled(cron = "${codesync.file-moving.cron}")
	public void runMoveFiles() {
		if (!schedularEnabled)
			return;
		try {
			CodeSyncLogger.logInfo("MoveExpiredFilesScheduler: running expiry check...");
			int count = fileService.moveExpiredFiles();
			if (count > 0) {
				CodeSyncLogger.logInfo("MoveExpiredFilesScheduler: processed " + count + " file(s).");
			} else {
				CodeSyncLogger.logInfo("MoveExpiredFilesScheduler: no files to process.");
			}
		} catch (Exception e) {
			CodeSyncLogger.logError(MoveExpiredFilesScheduler.class, "MoveExpiredFilesScheduler Exception", e);
		}
	}
}