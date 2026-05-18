package com.cs.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.cs.config.CodeSyncLogger;

/**
 * Handles all application-level exceptions.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ShareNotFoundException.class)
	public ResponseEntity<String> handleNotFound(ShareNotFoundException ex) {
		CodeSyncLogger.logError(ex.getMessage(), ex);
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<String> handleBadKey(IllegalArgumentException ex) {
		CodeSyncLogger.logError(ex.getMessage(), ex);
		return ResponseEntity.badRequest().body("Invalid Share Key");
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<String> handleGeneric(Exception ex) {
		CodeSyncLogger.logError("Unhandled error", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
	}

	@SuppressWarnings("deprecation")
	@ExceptionHandler(FileSizeExceededException.class)
	public ResponseEntity<?> handleFileSize(FileSizeExceededException ex) {

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", LocalDateTime.now());
		body.put("status", 413);
		body.put("error", "Payload Too Large");
		body.put("message", ex.getMessage());

		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
	}
}
