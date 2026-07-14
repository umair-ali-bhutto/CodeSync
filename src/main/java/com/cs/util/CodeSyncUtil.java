package com.cs.util;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.cs.config.CodeSyncLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Utility class for common CodeSync security and validation operations.
 * <p>
 * Contains helper methods used across the security layer for:
 * <ul>
 * <li>Share key validation</li>
 * <li>Safe logging of oversized keys</li>
 * <li>Generating a standard HTML 401 Unauthorized response</li>
 * <li>Basic IP header validation</li>
 * </ul>
 */
@Component
public class CodeSyncUtil {

	/**
	 * Maximum allowed length for a share key to prevent abuse, malformed URLs, or
	 * potential injection attempts.
	 */
	public static final int MAX_KEY_LENGTH = 100;

	/**
	 * Validates the share key length strictly.
	 *
	 * @param key the incoming share key from URL
	 * @throws IllegalArgumentException if the key exceeds {@link #MAX_KEY_LENGTH}
	 */
	public static void validateKey(String key) {
		if (key.length() > MAX_KEY_LENGTH) {
			throw new IllegalArgumentException("Invalid share key length");
		}
	}

	/**
	 * Validates the share key length without throwing an exception.
	 * <p>
	 * If the key exceeds the allowed length, it logs the event and returns a safe
	 * placeholder message instead of the original key.
	 *
	 * @param key the incoming share key from URL
	 * @return original key if valid, otherwise a safe shortened message
	 */
	public static String validateKeyWithoutException(String key) {
		String uri = key;
		try {
			if (uri.length() > MAX_KEY_LENGTH) {
				CodeSyncLogger.logInfo("URI TOO LONG LENGTH: " + uri.length() + " URI = " + uri);
				uri = "URI TOO LONG: " + uri.length();
			}
		} catch (Exception e) {
			CodeSyncLogger.logError(CodeSyncUtil.class, "EXCEPTION", e);
			uri = e.getMessage();
		}

		return uri;
	}

	/**
	 * Generates and writes a standardized HTML 401 Unauthorized error page directly
	 * to the HTTP response.
	 * <p>
	 * This method is used by {@link com.cs.config.JwtAuthenticationEntryPoint} to
	 * provide a user-friendly error page when unauthorized access occurs.
	 *
	 * @param response HttpServletResponse to write the HTML content into
	 * @return the same response object after modification
	 * @throws IOException if writing to the response fails
	 */
	public static HttpServletResponse getHtmlErrorPage(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		// Set response to HTML content type
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		String exampleUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
				+ request.getContextPath() + "/share/yourendpoint";
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
				+ "					<li>Include a valid endpoint. http://IP:PORT/" + request.getContextPath()
				+ "/share/yourendpoint </li>\n" + "					<li>Example: <code> " + exampleUrl
				+ " </code></li>\n" + "				</ul>\n" + "			</body>\n" + "			</html>\n"
				+ "			");
		return response;
	}

	/**
	 * Basic validation for IP-related headers to ensure the value is usable and not
	 * empty or marked as "unknown".
	 *
	 * @param value IP header value
	 * @return true if valid, false otherwise
	 */
	public static boolean isIpValid(String value) {
		return value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value);
	}

	/**
	 * Resolves real client IP (supports reverse proxy).
	 */
	public static String getClientIp(HttpServletRequest request) {

		// 1️⃣ X-Forwarded-For (may contain multiple IPs)
		String xff = request.getHeader("X-Forwarded-For");
		if (CodeSyncUtil.isIpValid(xff)) {
			String ip = xff.split(",")[0].trim();
			CodeSyncLogger.logInfo("Client IP from X-Forwarded-For: " + ip);
			return ip;
		}

		// 2️⃣ X-Real-IP (Nginx)
		String xRealIp = request.getHeader("X-Real-IP");
		if (CodeSyncUtil.isIpValid(xRealIp)) {
			CodeSyncLogger.logInfo("Client IP from X-Real-IP: " + xRealIp);
			return xRealIp;
		}

		// 3️⃣ Forwarded (RFC 7239)
		String forwarded = request.getHeader("Forwarded");
		if (CodeSyncUtil.isIpValid(forwarded)) {
			// Example: for=192.168.1.10;proto=https
			for (String part : forwarded.split(";")) {
				if (part.trim().startsWith("for=")) {
					String ip = part.replace("for=", "").replace("\"", "").trim();
					CodeSyncLogger.logInfo("Client IP from Forwarded: " + ip);
					return ip;
				}
			}
		}

		// 4️⃣ Fallback
		String remoteAddr = request.getRemoteAddr();
		CodeSyncLogger.logInfo("Client IP from request.getRemoteAddr(): " + remoteAddr);
		return remoteAddr;
	}

	/**
	 * Generates Unique Reference For Block ID
	 */
	public static String generateBlockRef(String clientIp) {
		String datePart = java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		String timePart = String.valueOf(System.currentTimeMillis()).substring(8);
		return "CS-BLK-" + datePart + "-" + timePart + "_" + clientIp;
	}
}
