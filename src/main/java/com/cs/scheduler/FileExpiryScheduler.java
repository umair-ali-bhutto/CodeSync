package com.cs.scheduler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cs.config.CodeSyncLogger;
import com.cs.service.CodeSyncSharedFileService;

/**
 * Runs periodically to mark expired files as inactive. Interval is configurable
 * via application.properties: codesync.file-expiry.cron
 */
@Component
public class FileExpiryScheduler {

	@Value("${codesync.file-expiry.cron.enabled:false}")
	private Boolean schedularEnabled;

	private final CodeSyncSharedFileService fileService;

	public FileExpiryScheduler(CodeSyncSharedFileService fileService) {
		this.fileService = fileService;
	}

	@Scheduled(cron = "${codesync.file-expiry.cron}")
	public void runExpiryCheck() {
		if (!schedularEnabled)
			return;

		try {
			CodeSyncLogger.logInfo("FileExpiryScheduler: running expiry check...");
			int count = fileService.expireFiles();
			if (count > 0) {
				CodeSyncLogger.logInfo("FileExpiryScheduler: expired " + count + " file(s).");
			} else {
				CodeSyncLogger.logInfo("FileExpiryScheduler: no files to expire.");
			}
		} catch (Exception e) {
			CodeSyncLogger.logError(FileExpiryScheduler.class, "FileExpiryScheduler", e);
		}

	}
}