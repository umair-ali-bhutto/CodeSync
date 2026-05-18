Here's the updated `archiveFile` method that tries WinSCP first, and falls back to your local archive folder if it fails:

```java
@Value("${codesync.archive-dir}")
private String archiveDirectory;

@Value("${codesync.winscp.enabled:false}")
private boolean winScpEnabled;

@Value("${codesync.winscp.exe-path:C:\\Program Files (x86)\\WinSCP\\WinSCP.com}")
private String winScpExePath;

@Value("${codesync.winscp.sftp-host:172.191.1.223}")
private String winScpHost;

@Value("${codesync.winscp.sftp-user:umair.ali}")
private String winScpUser;

@Value("${codesync.winscp.sftp-password:}")
private String winScpPassword;

@Value("${codesync.winscp.remote-base-path:/mnt/8EFED7B1FED79037/UBUNTU-BACKUP/shared/223/scp-test}")
private String winScpRemoteBasePath;

// ---- Archive helper ----
private void archiveFile(CodeSyncSharedFile f) {
    Path source = Paths.get(f.getStoredPath());
    if (!Files.exists(source)) {
        CodeSyncLogger.logInfo("archiveFile: source not found, skipping: " + source);
        return;
    }

    if (winScpEnabled) {
        boolean scpSuccess = transferViaWinScp(f, source);
        if (scpSuccess) return; // done — file moved to Ubuntu and deleted locally
        CodeSyncLogger.logInfo("archiveFile: WinSCP failed, falling back to local archive.");
    }

    // Fallback — move to local archive folder
    moveToLocalArchive(f, source);
}

// ---- WinSCP transfer ----
private boolean transferViaWinScp(CodeSyncSharedFile f, Path source) {
    Path scriptFile = null;
    try {
        // Remote directory mirrors shareKey structure
        String remoteDir  = winScpRemoteBasePath + "/" + f.getShareKey();
        String remotePath = remoteDir + "/" + source.getFileName().toString();

        // Build the WinSCP script dynamically
        String script = String.join("\n",
            "option batch on",
            "option confirm off",
            "open sftp://" + winScpUser + "@" + winScpHost + "/ -password=\"" + winScpPassword + "\"",
            "mkdir " + remoteDir,           // create remote dir if not exists (fails silently with batch on)
            "put \"" + source.toAbsolutePath() + "\" " + remotePath,
            "exit"
        );

        // Write script to a temp file
        scriptFile = Files.createTempFile("winscp_", ".txt");
        Files.writeString(scriptFile, script);

        CodeSyncLogger.logInfo("archiveFile: running WinSCP for file: " + source.getFileName());

        // Build and run the process
        ProcessBuilder pb = new ProcessBuilder(
            winScpExePath,
            "/script=" + scriptFile.toAbsolutePath()
        );
        pb.redirectErrorStream(true); // merge stderr into stdout

        Process process = pb.start();

        // Capture output for logging
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            CodeSyncLogger.logInfo("archiveFile: WinSCP timed out after 120s.\n" + output);
            return false;
        }

        int exitCode = process.exitValue();
        CodeSyncLogger.logInfo("archiveFile: WinSCP exit code=" + exitCode + "\n" + output);

        if (exitCode != 0) {
            CodeSyncLogger.logInfo("archiveFile: WinSCP non-zero exit, treating as failure.");
            return false;
        }

        // Success — delete local file and update DB path
        Files.deleteIfExists(source);
        f.setStoredPath(remotePath); // store the remote SFTP path in DB
        CodeSyncLogger.logInfo("archiveFile: WinSCP success. Remote path saved: " + remotePath);
        return true;

    } catch (Exception e) {
        CodeSyncLogger.logError(getClass(), "archiveFile WinSCP Exception", e);
        return false;
    } finally {
        // Always clean up the temp script file
        if (scriptFile != null) {
            try { Files.deleteIfExists(scriptFile); } catch (Exception ignored) {}
        }
    }
}

// ---- Local archive fallback ----
private void moveToLocalArchive(CodeSyncSharedFile f, Path source) {
    try {
        Path archiveDir = Paths.get(archiveDirectory, f.getShareKey());
        Files.createDirectories(archiveDir);

        Path destination = archiveDir.resolve(source.getFileName());
        if (Files.exists(destination)) {
            String ts = String.valueOf(System.currentTimeMillis());
            destination = archiveDir.resolve(ts + "_" + source.getFileName());
        }

        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        f.setStoredPath(destination.toString());
        CodeSyncLogger.logInfo("archiveFile: moved to local archive: " + destination);

    } catch (Exception e) {
        CodeSyncLogger.logError(getClass(), "archiveFile local fallback Exception", e);
    }
}
```

---

**application.properties:**

```properties
# Local archive fallback
codesync.archive-dir=./uploads/ARCHIVE

# WinSCP config
codesync.winscp.enabled=true
codesync.winscp.exe-path=C:\\Program Files (x86)\\WinSCP\\WinSCP.com
codesync.winscp.sftp-host=172.191.1.223
codesync.winscp.sftp-user=umair.ali
codesync.winscp.sftp-password=mypass+
codesync.winscp.remote-base-path=/mnt/8EFED7B1FED79037/UBUNTU-BACKUP/shared/223/scp-test
```

---

The flow is:

```
archiveFile()
    │
    ├─ winScpEnabled=true?
    │       │
    │       ├─ write temp script → run WinSCP.com → wait up to 120s
    │       │
    │       ├─ exit code 0 + no timeout?
    │       │       └─ YES → delete local file, save remote path to DB ✅
    │       │
    │       └─ anything fails?
    │               └─ log it → fall through to local archive
    │
    └─ moveToLocalArchive() → moves to ARCHIVE/{shareKey}/ locally
```

One thing to be careful about — the SFTP password is in plaintext in `application.properties`. Make sure that file is not committed to Git. If you want to avoid plaintext you can use an environment variable instead:

```properties
codesync.winscp.sftp-password=${CODESYNC_SFTP_PASSWORD}
```

And set `CODESYNC_SFTP_PASSWORD=mypass+` as a system/environment variable on the Windows server.