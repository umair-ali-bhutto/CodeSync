package com.ag.config;

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

	private static final long serialVersionUID = -7858869558953243875L;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		// Set response to HTML content type
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

		// Write HTML response explaining the correct way to use the URL
		response.getWriter().write("\n" + "			<!DOCTYPE html>\n" + "			<html lang=\"en\">\n"
				+ "			<head>\n" + "				<meta charset=\"UTF-8\">\n"
				+ "				<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
				+ "				<title>Unauthorized Access</title>\n" + "				<style>\n"
				+ "					body { font-family: Arial, sans-serif; margin: 20px; }\n"
				+ "					h1 { color: #d9534f; }\n" + "					p { margin-bottom: 10px; }\n"
				+ "					code { background-color: #f8f8f8; padding: 2px 4px; border-radius: 3px; }\n"
				+ "				</style>\n" + "			</head>\n" + "			<body>\n"
				+ "				<h1>401 Unauthorized</h1>\n"
				+ "				<p>You are not authorized to access this resource.</p>\n"
				+ "				<p><strong>Correct way to use this URL:</strong></p>\n" + "				<ul>\n"
				+ "					<li>Include a valid endpoint. http://IP:PORT/codesync/yourendpoint </li>\n"
				+ "					<li>Example: <code> http://172.190.1.223:8087/codesync/yourendpoint </code></li>\n"
				+ "				</ul>\n" + "			</body>\n" + "			</html>\n" + "			");
	}
}