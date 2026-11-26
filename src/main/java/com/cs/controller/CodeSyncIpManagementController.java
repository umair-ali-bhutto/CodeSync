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

import jakarta.servlet.http.HttpServletRequest;

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

	// ── add client ──
	@PostMapping("/client/add")
	public String addClient(@RequestParam("ip") String ip, @RequestParam("name") String name, RedirectAttributes ra) {
		try {
			service.addClient(ip, name);
			CodeSyncLogger.logDebug("Client added: " + ip);
			ra.addFlashAttribute("successMsg", "Client " + ip.trim() + " added.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("errorMsg", e.getMessage());
		}
		return "redirect:/admin/ip-management";
	}

	// ── edit client ──
	@PostMapping("/client/update")
	public String updateClient(@RequestParam("ip") String ip, @RequestParam("name") String name,
			HttpServletRequest request, RedirectAttributes ra) {
		try {
			service.updateClient(ip, name, resolveClientIp(request));
			CodeSyncLogger.logDebug("Client updated: " + ip);
			ra.addFlashAttribute("successMsg", "Client " + ip.trim() + " updated.");
		} catch (IllegalArgumentException e) {
			ra.addFlashAttribute("errorMsg", e.getMessage());
		}
		return "redirect:/admin/ip-management";
	}

	private String resolveClientIp(HttpServletRequest request) {
		String xff = request.getHeader("X-Forwarded-For");
		if (xff != null && !xff.isBlank()) {
			return xff.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}
}
