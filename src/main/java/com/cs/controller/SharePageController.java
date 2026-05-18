package com.cs.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.cs.config.CodeSyncLogger;
import com.cs.util.CodeSyncUtil;

/**
 * Serves the share editor UI.
 */
@Controller
public class SharePageController {

	@Value("${codesync.max-file-size}")
	private DataSize maxFileSize;

	@Value("${codesync.max-total-files}")
	private int maxFilesPerShare;

	/**
	 * Loads the editor page for a given share key.
	 *
	 * @param key   share key
	 * @param model view model
	 * @return Thymeleaf template name
	 */
	@GetMapping("/share/{key}")
	public String sharePage(@PathVariable String key, Model model) {
		CodeSyncLogger.logInfo("Loading editor page for key: " + key);
		CodeSyncUtil.validateKey(key);
		model.addAttribute("shareKey", key);

		model.addAttribute("maxFileBytes", maxFileSize.toBytes());
		model.addAttribute("maxFileMegaBytes", maxFileSize.toMegabytes());
		model.addAttribute("maxFilesPerShare", maxFilesPerShare);
		model.addAttribute("maxQueueSize", maxFilesPerShare);

		
		

		return "sharePage";
	}
}
