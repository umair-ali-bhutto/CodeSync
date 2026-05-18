package com.cs.exception;

public class FileSizeExceededException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public FileSizeExceededException(long maxMb) {
		super("File exceeds maximum allowed size of " + maxMb + " MB.");
	}
}