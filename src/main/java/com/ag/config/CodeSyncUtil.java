package com.ag.config;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;

@Component
public class CodeSyncUtil {

	public static final int MAX_KEY_LENGTH = 100;

	public static void validateKey(String key) {
		if (key.length() > MAX_KEY_LENGTH) {
			throw new IllegalArgumentException("Invalid share key length");
		}
	}

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

	public static HttpServletResponse getHtmlErrorPage(HttpServletResponse response) throws IOException {
		// Set response to HTML content type
		response.setContentType("text/html");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
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
				+ "					<li>Include a valid endpoint. http://IP:PORT/codesync/share/yourendpoint </li>\n"
//				+ "					<li>Example: <code> https://demo.accessgroup.mobi/codesync/share/yourendpoint </code></li>\n"
				+ "					<li>Example: <code> http://172.191.1.223:8081/codesync/share/yourendpoint </code></li>\n"
				+ "				</ul>\n" + "			</body>\n" + "			</html>\n" + "			");
		return response;
	}
}
