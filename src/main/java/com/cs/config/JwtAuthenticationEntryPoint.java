package com.cs.config;

import java.io.IOException;
import java.io.Serializable;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.cs.util.CodeSyncUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Spring Security authentication entry point used to handle unauthorized
 * access attempts.
 * <p>
 * Behaviour varies by request type:
 * <ul>
 * <li>API requests ({@code /api/**}, {@code /logsService/**}) → JSON 401</li>
 * <li>Share requests ({@code /share/**}) → HTML usage-instructions page</li>
 * <li>Browser requests (everything else) → redirect to {@code /login}</li>
 * </ul>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

	private static final long serialVersionUID = -7858869558953243875L;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		String acceptHeader = request.getHeader("Accept");
		String requestedWith = request.getHeader("X-Requested-With");
		String uri = request.getRequestURI();

		boolean isLoginPage = uri.contains("/login");
		boolean isApiRequest = uri.startsWith("/api/") || uri.startsWith("/logsService");
		boolean isShareRequest = uri.startsWith(request.getContextPath() + "/share/") || uri.startsWith("/share/");
		boolean isBrowserRequest = acceptHeader != null && acceptHeader.contains("text/html")
				&& !"XMLHttpRequest".equals(requestedWith);

		if (isApiRequest) {
			// ── REST / programmatic callers ──────────────────────────────────
			writeJsonUnauthorized(response);

		} else if (isBrowserRequest && isShareRequest) {
			// ── Public share links opened without a token ────────────────────
			CodeSyncUtil.getHtmlErrorPage(request, response);

		} else if (isBrowserRequest && !isLoginPage) {
			// ── Normal browser navigation ────────────────────────────────────
			response.sendRedirect(request.getContextPath() + "/login");

		} else {
			// ── Fallback (e.g. XMLHttpRequest to a non-/api/ route) ──────────
			writeJsonUnauthorized(response);
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void writeJsonUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("""
				{
				  "status": 401,
				  "error": "Unauthorized",
				  "message": "Authentication required. Please provide a valid Bearer token."
				}
				""");
	}
}