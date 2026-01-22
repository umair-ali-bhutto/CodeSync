package com.ag.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.ag.config.CodeSyncLogger;

@Controller
public class SharePageController {

	@GetMapping("/{key}")
	public String sharePage(@PathVariable String key, Model model) {
		CodeSyncLogger.logInfo("SHAREPAGE: "+key);
		model.addAttribute("shareKey", key);
		return "sharePage";
	}
}
