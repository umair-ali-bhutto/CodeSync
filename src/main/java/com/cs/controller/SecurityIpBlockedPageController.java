package com.cs.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class SecurityIpBlockedPageController {

	@GetMapping("/blocked-ip")
	public String blockedIpPage(HttpServletRequest request, Model model) {

		String ip = (String) request.getAttribute("blockedIp");

		if (ip == null) {
			return "redirect:/login";
		}

		String blockId = (String) request.getAttribute("blockId");

		if (blockId == null) {
			return "redirect:/login";
		}

		model.addAttribute("errorCode", 403);
		model.addAttribute("errorTitle", "IP BLOCKED 🚫");
		model.addAttribute("errorDescription", "Your IP (" + ip + ") is blocked.");
		model.addAttribute("errorRef", blockId);

		return "ip-blocked";
	}
}