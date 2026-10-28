package com.cs.exception;

import com.cs.config.CodeSyncLogger;

public class InsufficientStorageException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final long usableBytes;
	private final long requiredBytes;
	private final long incomingFileBytes;

	public InsufficientStorageException(long usableBytes, long requiredBytes, long incomingFileBytes) {
		CodeSyncLogger.logInfo(String.format(
				"Not enough disk space, please contact Administrator. Available: %.2f MB, Required: %.2f MB (file: %.2f MB + safety buffer).",
				usableBytes / (1024.0 * 1024.0), requiredBytes / (1024.0 * 1024.0),
				incomingFileBytes / (1024.0 * 1024.0)));
		super(String.format(
				"Not enough disk space, please contact Administrator. Available: %.2f MB, Required: %.2f MB (file: %.2f MB + safety buffer).",
				usableBytes / (1024.0 * 1024.0), requiredBytes / (1024.0 * 1024.0),
				incomingFileBytes / (1024.0 * 1024.0)));
		this.usableBytes = usableBytes;
		this.requiredBytes = requiredBytes;
		this.incomingFileBytes = incomingFileBytes;
	}

	public long getUsableBytes() {
		return usableBytes;
	}

	public long getRequiredBytes() {
		return requiredBytes;
	}

	public long getIncomingFileBytes() {
		return incomingFileBytes;
	}
}