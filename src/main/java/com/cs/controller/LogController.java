package com.cs.controller;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cs.config.CodeSyncLogger;
import com.cs.config.StartUpInit;
import com.cs.dto.LogToggleRequest;
import com.cs.util.CodeSyncUtil;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST Controller to enable or disable application logging at runtime.
 */
@RestController
@RequestMapping("/logsService")
public class LogController {

	/**
	 * Logs a message without disturbing the current log state. - If logs are ON →
	 * logs normally, state unchanged. - If logs are OFF → temporarily enables,
	 * logs, restores to OFF.
	 */
	private void logWithRestore(String message) {
		boolean logsWereEnabled = "Y".equals(StartUpInit.getEnableLogs());
		if (!logsWereEnabled) {
			StartUpInit.setEnableLogs("Y");
		}
		CodeSyncLogger.logInfo(message);
		if (!logsWereEnabled) {
			StartUpInit.setEnableLogs("N");
		}
	}

	/**
	 * Enables or disables logs based on the request payload.
	 *
	 * Expected JSON: { "enable.logs": "Y" }
	 *
	 * Only "Y" or "N" (case-insensitive) are allowed.
	 *
	 * @param body    JSON request body
	 * @param request HTTP request
	 * @return ResponseEntity<Void>
	 */
	@PostMapping
	public ResponseEntity<Void> saveOrUpdate(@RequestBody LogToggleRequest body, HttpServletRequest request) {
		try {
			String clientIp = CodeSyncUtil.getClientIp(request);
			if (!CodeSyncUtil.getLocalAllowedIps().contains(clientIp)) {
				logWithRestore("LogsService blocked request from IP: " + clientIp);
				return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
			}

			if (body == null || body.getEnableLogs() == null) {
				logWithRestore("Logs service called with missing 'enable.logs'.");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
			}

			String value = body.getEnableLogs().trim().toUpperCase();
			if (!value.equals("Y") && !value.equals("N")) {
				logWithRestore("Invalid value for enable.logs: " + value);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
			}

			logWithRestore("Logs service called. Setting enableLogs=" + value);
			StartUpInit.setEnableLogs(value);

		} catch (Exception e) {
			CodeSyncLogger.logError(LogController.class, "Failed to update log settings", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}
		return ResponseEntity.ok().build();
	}
}