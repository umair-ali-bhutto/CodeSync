package com.ag.exception;

/**
 * Thrown when a CodeSync share key does not exist.
 */
public class ShareNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ShareNotFoundException(String key) {
        super("Share not found for key: " + key);
    }
}
