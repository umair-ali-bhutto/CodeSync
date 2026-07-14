package com.cs.controller;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.cs.config.CodeSyncLogger;
import com.cs.service.CodeSyncIpManagementService;

@Controller
@RequestMapping("/admin/ip-management")
@PreAuthorize("hasRole('ADMIN')")
public class CodeSyncIpManagementController {

	private final CodeSyncIpManagementService service;

	public CodeSyncIpManagementController(CodeSyncIpManagementService service) {
		this.service = service;
	}

	// ── page ──────────────────────────────────────────────────────────────────

	@GetMapping
	public String page(Model model) {
		var blockedIps = service.getAllBlockedIps();
		Set<String> blockedIpSet = blockedIps.stream().map(b -> b.getIp()).collect(Collectors.toSet());

		model.addAttribute("blockedIps", blockedIps);
		model.addAttribute("blockedIpSet", blockedIpSet); // ← new
		model.addAttribute("knownClients", service.getKnownClients());
		model.addAttribute("unknownIps", service.getUnknownIps());
		return "admin/ip-management";
	}

	// ── block ─────────────────────────────────────────────────────────────────

	@PostMapping("/block")
	public String block(@RequestParam("ip") String ip, @RequestParam(value = "reason", defaultValue = "") String reason,
			@AuthenticationPrincipal UserDetails admin, RedirectAttributes ra) {

		CodeSyncLogger.logDebug("IP: " + ip + " has been blocked.");

		service.blockIp(ip.trim(), admin.getUsername(), reason.trim());
		ra.addFlashAttribute("successMsg", "IP " + ip + " has been blocked.");
		return "redirect:/admin/ip-management";
	}

	// ── unblock ───────────────────────────────────────────────────────────────

	@PostMapping("/unblock")
	public String unblock(@RequestParam("ip") String ip, RedirectAttributes ra) {

		CodeSyncLogger.logDebug("IP: " + ip + " has been unblocked.");

		service.unblockIp(ip.trim());
		ra.addFlashAttribute("successMsg", "IP " + ip + " has been unblocked.");
		return "redirect:/admin/ip-management";
	}
}
