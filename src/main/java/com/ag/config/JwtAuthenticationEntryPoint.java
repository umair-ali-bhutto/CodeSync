package com.ag.config;

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Custom Spring Security authentication entry point used to handle unauthorized
 * access attempts.
 * <p>
 * Instead of returning the default JSON or plain 401 response, this class
 * returns a user-friendly HTML error page explaining how to correctly access
 * the application endpoints.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

	private static final long serialVersionUID = -7858869558953243875L;

	/**
	 * Triggered automatically by Spring Security when an unauthenticated user
	 * attempts to access a protected resource.
	 * <p>
	 * This method delegates the response rendering to
	 * {@link CodeSyncUtil#getHtmlErrorPage(HttpServletResponse)} to return a custom
	 * HTML error page with usage instructions.
	 *
	 * @param request       the incoming HTTP request
	 * @param response      the HTTP response to be written
	 * @param authException the exception that caused the authentication failure
	 * @throws IOException if writing the response fails
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response = CodeSyncUtil.getHtmlErrorPage(response);
	}
}