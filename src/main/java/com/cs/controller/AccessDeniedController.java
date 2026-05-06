package com.cs.controller;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AccessDeniedController {

	@GetMapping("/access-denied")
	public String accessDenied(Model model, HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		String user = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "Unknown";

		model.addAttribute("user", user);

		response.setContentType("text/html;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);

		return "access-denied";
	}
}
