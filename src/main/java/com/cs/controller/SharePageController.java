package com.cs.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.cs.config.CodeSyncLogger;
import com.cs.util.CodeSyncUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Serves the share editor UI.
 */
@Controller
public class SharePageController {

	@Value("${codesync.max-file-size}")
	private DataSize maxFileSize;

	@Value("${codesync.max-total-files}")
	private int maxFilesPerShare;

	@Value("${codesync.version}")
	private String version;

	@Value("${codesync.version-date}")
	private String versionDate;
	
	@Value("${codesync.autoreload.enabled:true}")
	private boolean autoReloadEnabled;

	@Value("${codesync.autoreload.max-consecutive-errors:10}")
	private int autoReloadMaxErrors;

	@Value("${codesync.autoreload.countdown-seconds:5}")
	private int autoReloadCountdown;

	@Value("${codesync.autoreload.max-reloads:3}")
	private int autoReloadMaxReloads;

	@Value("${codesync.autoreload.reload-window-seconds:600}")
	private int autoReloadWindowSeconds;

	@Value("${codesync.autoreload.require-reachable:false}")
	private boolean autoReloadRequireReachable;

	@Value("${codesync.autoreload.ignored-statuses:}")
	private List<Integer> autoReloadIgnoredStatuses;

	/**
	 * Loads the editor page for a given share key.
	 *
	 * @param key   share key
	 * @param model view model
	 * @return Thymeleaf template name
	 */
	@GetMapping("/share/{key}")
	public String sharePage(@PathVariable String key, Model model, HttpServletRequest request) {
		CodeSyncLogger.logInfo("Loading editor page for key: " + key);
		CodeSyncUtil.validateKey(key);

		String exampleUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
				+ request.getContextPath() + "/share/" + key;

		model.addAttribute("shareKey", key);
		model.addAttribute("contextPath", request.getContextPath());
		model.addAttribute("url", exampleUrl);
		model.addAttribute("version", version);
		model.addAttribute("versionDate", versionDate);
		model.addAttribute("maxFileBytes", maxFileSize.toBytes());
		model.addAttribute("maxFileMegaBytes", maxFileSize.toMegabytes());
		model.addAttribute("maxFilesPerShare", maxFilesPerShare);
		model.addAttribute("maxQueueSize", maxFilesPerShare);
		model.addAttribute("autoReloadEnabled", autoReloadEnabled);
		model.addAttribute("autoReloadMaxErrors", autoReloadMaxErrors);
		model.addAttribute("autoReloadCountdown", autoReloadCountdown);
		model.addAttribute("autoReloadMaxReloads", autoReloadMaxReloads);
		model.addAttribute("autoReloadWindowMs", autoReloadWindowSeconds * 1000L);
		model.addAttribute("autoReloadRequireReachable", autoReloadRequireReachable);
		model.addAttribute("autoReloadIgnoredStatuses", autoReloadIgnoredStatuses);
		
		return "sharePage";
	}
}
