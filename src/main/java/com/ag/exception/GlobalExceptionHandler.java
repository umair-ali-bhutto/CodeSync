package com.ag.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ag.config.CodeSyncLogger;

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
}
