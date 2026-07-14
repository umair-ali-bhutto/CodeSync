package com.cs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

	// ── 404 Not Found ──────────────────────────────────────────────────────────
	@ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleNotFound(HttpServletRequest request, Model model) {
		return error(model, 404, "Page Not Found", "The page you're looking for doesn't exist or has been moved.");
	}

	// ── 403 Access Denied (browser requests only) ──────────────────────────────
	// NOTE: API 403s are handled by SecurityConfig's accessDeniedHandler
	@ExceptionHandler(AccessDeniedException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public String handleAccessDenied(HttpServletRequest request, Model model) {
		return error(model, 403, "Access Denied", "You don't have permission to access this resource.");
	}

	// ── 401 Unauthorized ───────────────────────────────────────────────────────
	@ExceptionHandler(AuthenticationException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public String handleUnauthorized(HttpServletRequest request, Model model) {
		return error(model, 401, "Unauthorized", "You must be logged in to access this page.");
	}

	// ── 400 Bad Request ────────────────────────────────────────────────────────
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public String handleBadRequest(HttpServletRequest request, Model model) {
		return error(model, 400, "Bad Request",
				"The request could not be understood or was missing required parameters.");
	}

	// ── ShareNotFoundException → 404 ───────────────────────────────────────────
	@ExceptionHandler(ShareNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public String handleShareNotFound(ShareNotFoundException ex, Model model) {
		return error(model, 404, "Share Not Found", ex.getMessage());
	}

	// ── 500 Catch-all ──────────────────────────────────────────────────────────
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public String handleGeneral(HttpServletRequest request, HttpServletResponse response, Exception ex, Model model) {
		return error(model, 500, "Something Went Wrong",
				"An unexpected error occurred. Please try again or contact support.");
	}

	// ── Helper ─────────────────────────────────────────────────────────────────
	private String error(Model model, int code, String title, String description) {
		model.addAttribute("errorCode", code);
		model.addAttribute("errorTitle", title);
		model.addAttribute("errorDescription", description);
		model.addAttribute("backUrl", resolveBackUrl(code));
		return "error"; // → templates/error.html
	}

	private String resolveBackUrl(int code) {
		return switch (code) {
		case 401 -> "/login";
		case 403 -> "/admin/dashboard";
		default -> "/admin/dashboard";
		};
	}
}