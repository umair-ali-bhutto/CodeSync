package com.cs.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.persistence.NoResultException;

/**
 * Utility class for logging info, debug, and error messages using Log4j.
 */
public class CodeSyncLogger {

	/**
	 * Logs an info message with the given class context.
	 *
	 * @param obj     the class used as the logger context
	 * @param message the info message to log
	 */
	public static void logInfo(@SuppressWarnings("rawtypes") java.lang.Class obj, String message) {
		if (StartUpInit.getEnableLogs().equals("Y")) {
			final Logger logger = LogManager.getLogger(obj);
			if (logger.isInfoEnabled()) {
				logger.info(message);
			}
		}
	}

	/**
	 * Logs an info message with CodeSyncLogger as context.
	 *
	 * @param message the info message to log
	 */
	public static void logInfo(String message) {
		if (StartUpInit.getEnableLogs().equals("Y")) {
			final Logger logger = LogManager.getLogger(CodeSyncLogger.class);
			if (logger.isInfoEnabled()) {
				logger.info(message);
			}
		}
	}

	/**
	 * Logs a debug message with CodeSyncLogger as context.
	 *
	 * @param message the debug message to log
	 */
	public static void logDebug(String message) {
		if (StartUpInit.getEnableLogs().equals("Y")) {
			final Logger logger = LogManager.getLogger(CodeSyncLogger.class);
			if (logger.isDebugEnabled()) {
				logger.debug(message);
			}
		}
	}

	/**
	 * Logs a debug message with the given class context.
	 *
	 * @param obj     the class used as the logger context
	 * @param message the debug message to log
	 */
	public static void logDebug(@SuppressWarnings("rawtypes") java.lang.Class obj, String message) {
		if (StartUpInit.getEnableLogs().equals("Y")) {
			final Logger logger = LogManager.getLogger(obj);
			if (logger.isDebugEnabled()) {
				logger.debug(message);
			}
		}
	}

	/**
	 * Logs an error message with CodeSyncLogger as context. Handles
	 * NoResultException specially.
	 *
	 * @param message the error message to log
	 * @param e       the exception to log
	 */
	public static void logError(String message, Exception e) {
		final Logger logger = LogManager.getLogger(CodeSyncLogger.class);
		logger.error(message + "|" + e.getLocalizedMessage());
		if (e instanceof NoResultException) {
		} else {
			logger.error(e);
			e.printStackTrace();
		}
	}

	/**
	 * Logs an error message with the given class context. Handles NoResultException
	 * specially.
	 *
	 * @param obj     the class used as the logger context
	 * @param message the error message to log
	 * @param e       the exception to log
	 */
	public static void logError(@SuppressWarnings("rawtypes") java.lang.Class obj, String message, Exception e) {
		final Logger logger = LogManager.getLogger(obj);
		logger.error(message + "|" + e.getLocalizedMessage());
		if (e instanceof NoResultException) {
		} else {
			logger.error(e);
			e.printStackTrace();
		}
	}
}
