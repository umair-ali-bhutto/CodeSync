package com.cs.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds an "announcement" object to the model of every view-returning
 * controller, so the share page controller does not need any changes.
 *
 * To limit it to one controller, use: @ControllerAdvice(assignableTypes =
 * SharePageController.class)
 */
@ControllerAdvice
public class AnnouncementAdvice {

	@Value("${codesync.announcement.enabled:false}")
	private boolean enabled;

	@Value("${codesync.announcement.title:Something BIG Is Coming!}")
	private String title;

	@Value("${codesync.announcement.message:CodeSync is preparing for its next chapter.}")
	private String message;

	@Value("${codesync.announcement.effective-date:}")
	private String effectiveDate;

	@Value("${codesync.announcement.required-phrase:I WILL BACK UP MY DATA}")
	private String requiredPhrase;

	@Value("${codesync.announcement.notice-key:codesync-upgrade-notice-}")
	private String noticeKey;

	@Value("${codesync.announcement.new-version-message:}")
	private String newVersionMessage;
	
	@Value("${codesync.version}")
	private String version;

	@Value("${codesync.version-date}")
	private String versionDate;
	
	

	@ModelAttribute("announcement")
	public Announcement announcement() {
		return new Announcement(enabled, title, message + " " + newVersionMessage, effectiveDate, effectiveDate, effectiveDate, requiredPhrase,
				noticeKey + versionDate + "--" + version);
	}

	public record Announcement(boolean enabled, String title, String message, String effectiveDate,
			String backupDeadline, String expiryDate, String requiredPhrase, String noticeKey) {
	}
}