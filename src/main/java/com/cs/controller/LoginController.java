package com.cs.controller;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LoginController {

	@GetMapping("/login")
	public String login(HttpServletRequest request, HttpServletResponse response) throws IOException {
		return "login";
	}
}