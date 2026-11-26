package com.cs.scheduler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cs.config.CodeSyncLogger;

@Component
public class DbMaintenanceScheduler {

	private static final int BATCH = 500;

	/**
	 * Backup and archive never run at the same time (also protects manual calls).
	 */
	private final ReentrantLock lock = new ReentrantLock();

	private final JdbcTemplate jdbc;

	@Value("${codesync.backup.dir}")
	private String backupDir;

	@Value("${codesync.backup.keep-days:30}")
	private int keepDays;

	/** zip | script-gz | script-xz | script-zstd */
	@Value("${codesync.backup.mode:zip}")
	private String backupMode;

	@Value("${codesync.backup.compress-timeout-minutes:60}")
	private int compressTimeoutMinutes;

	@Value("${codesync.archive.dir}")
	private String archiveDir;

	@Value("${codesync.archive.audit-retention-days:7}")
	private int auditRetentionDays;

	@Value("${codesync.archive.shared-file-retention-days:30}")
	private int sharedFileRetentionDays;

	@Value("${codesync.archive.keep-days:365}")
	private int archiveKeepDays;

	@Value("${spring.datasource.url}")
	private String dbUrl;

	@Value("${spring.datasource.username}")
	private String dbUser;

	@Value("${spring.datasource.password:}")
	private String dbPass;

	@Value("${codesync.backup.cron.enabled:false}")
	private Boolean backupEnabled;

	@Value("${codesync.archive.cron.enabled:false}")
	private Boolean archiveEnabled;

	public DbMaintenanceScheduler(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/* ====================================================================== */
	/* BACKUP SCHEDULAR */
	/* ====================================================================== */

	@Scheduled(cron = "${codesync.backup.cron:-}")
	public void backup() {
		if (!backupEnabled) {
			CodeSyncLogger.logInfo("[BACKUP] skipped: codesync.backup.cron.enabled=false");
			return;
		}
		if (!lock.tryLock()) {
			CodeSyncLogger.logInfo("[BACKUP] WARN skipped: another maintenance job is still running");
			return;
		}

		String runId = newRunId();
		long t0 = System.currentTimeMillis();
		try {
			Files.createDirectories(Path.of(backupDir));
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			String base = backupDir + "/codesync_" + ts;

			long dbSize = fileSize(dbFile());
			info("BACKUP", runId, "START mode=" + backupMode + " target=" + base + ".* mainDbSize=" + human(dbSize)
					+ " (" + dbFile() + ")");

			Path out = createBackup(base, runId);

			// verify
			if (!Files.exists(out) || Files.size(out) == 0) {
				throw new IllegalStateException("Backup file missing or empty: " + out);
			}
			long outSize = Files.size(out);
			String ratio = dbSize > 0 ? String.format("%.1f%%", outSize * 100.0 / dbSize) : "n/a";
			info("BACKUP", runId, "OK file=" + out + " size=" + human(outSize) + " ratioVsMainDb=" + ratio
					+ " durationMs=" + (System.currentTimeMillis() - t0));

			deleteOldBackups(runId);

			info("BACKUP", runId, "DONE totalDurationMs=" + (System.currentTimeMillis() - t0));
		} catch (Exception e) {
			error("BACKUP", runId, "FAILED after " + (System.currentTimeMillis() - t0) + "ms", e);
		} finally {
			lock.unlock();
		}
	}

	/* ====================================================================== */
	/* ARCHIVE SCHEDULAR */
	/* ====================================================================== */

	@Scheduled(cron = "${codesync.archive.cron:-}")
	public void archiveOldData() {
		if (!archiveEnabled) {
			CodeSyncLogger.logInfo("[ARCHIVE] skipped: codesync.archive.cron.enabled=false");
			return;
		}
		if (!lock.tryLock()) {
			CodeSyncLogger.logInfo("[ARCHIVE] WARN skipped: another maintenance job is still running");
			return;
		}

		String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

		String runId = newRunId();
		long t0 = System.currentTimeMillis();
		Path archiveFile = Path.of(archiveDir, "codesync_archive_" + ts + ".mv.db");
		// one archive file per day (use YearMonth.now() for one per month)
		String url = "jdbc:h2:file:" + archiveDir + "/codesync_archive_" + ts + ";AUTO_SERVER=TRUE";

		Result audit = null, shared = null;
		try {
			Files.createDirectories(Path.of(archiveDir));
			info("ARCHIVE", runId, "START auditRetentionDays=" + auditRetentionDays + " sharedFileRetentionDays="
					+ sharedFileRetentionDays + " mainDbSize=" + human(fileSize(dbFile())) + " target=" + url);

			try (Connection arch = DriverManager.getConnection(url, dbUser, dbPass)) {
				try (Statement st = arch.createStatement()) {
					st.execute(AUDIT_DDL);
					st.execute(
							"CREATE INDEX IF NOT EXISTS IDX_ARCH_AUDIT_CREATED ON CODE_SYNC_AUDIT_ARCHIVE(CREATED_AT)");
					st.execute(SHARED_FILE_DDL);
					st.execute(
							"CREATE INDEX IF NOT EXISTS IDX_ARCH_SF_UPLOADED ON CODE_SYNC_SHARED_FILE_ARCHIVE(UPLOADED_AT)");
					st.execute(
							"CREATE INDEX IF NOT EXISTS IDX_ARCH_SF_SHARE_KEY ON CODE_SYNC_SHARED_FILE_ARCHIVE(SHARE_KEY)");
				}

				// each table in its own try so a failure in one doesn't block the other
				try {
					Timestamp auditCutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(auditRetentionDays));
					audit = archiveTable(runId, arch, "CODE_SYNC_AUDIT", "CODE_SYNC_AUDIT_ARCHIVE", "CREATED_AT < ?",
							auditCutoff);
				} catch (Exception e) {
					error("ARCHIVE", runId, "CODE_SYNC_AUDIT FAILED (rows already committed to archive stay safe)", e);
				}

				try {
					Timestamp sfCutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(sharedFileRetentionDays));
					Timestamp now = new Timestamp(System.currentTimeMillis());
					// ONLY rows the app no longer needs: inactive/deleted or expired, AND old
					// enough
					shared = archiveTable(runId, arch, "CODE_SYNC_SHARED_FILE", "CODE_SYNC_SHARED_FILE_ARCHIVE",
							"UPLOADED_AT < ? AND (IS_ACTIVE = FALSE OR (EXPIRES_AT IS NOT NULL AND EXPIRES_AT < ?))",
							sfCutoff, now);
				} catch (Exception e) {
					error("ARCHIVE", runId, "CODE_SYNC_SHARED_FILE FAILED (rows already committed stay safe)", e);
				}
			}

			info("ARCHIVE", runId,
					"DONE audit=" + (audit == null ? "FAILED" : audit) + " sharedFile="
							+ (shared == null ? "FAILED" : shared) + " archiveFile=" + archiveFile + " archiveSize="
							+ human(fileSize(archiveFile)) + " mainDbSizeNow=" + human(fileSize(dbFile()))
							+ " totalDurationMs=" + (System.currentTimeMillis() - t0));

			deleteOldArchives(runId);
		} catch (Exception e) {
			error("ARCHIVE", runId, "FAILED after " + (System.currentTimeMillis() - t0) + "ms", e);
		} finally {
			lock.unlock();
		}
	}

	/* ====================================================================== */
	/* HELPERS */
	/* ====================================================================== */

	private Path createBackup(String base, String runId) throws Exception {
		switch (backupMode.toLowerCase()) {
		case "zip": {
			Path p = Path.of(base + ".zip");
			jdbc.execute("BACKUP TO '" + p + "'");
			return p;
		}
		case "script-gz": {
			Path p = Path.of(base + ".sql.gz");
			jdbc.execute("SCRIPT TO '" + p + "' COMPRESSION GZIP");
			return p;
		}
		case "script-xz":
			return scriptThenCompress(base, runId, ".xz", List.of("xz", "-9", "-f"));
		case "script-zstd":
			return scriptThenCompress(base, runId, ".zst", List.of("zstd", "-19", "--long=27", "--rm", "-f", "-q"));
		default:
			throw new IllegalArgumentException(
					"Unknown codesync.backup.mode=" + backupMode + " (use zip | script-gz | script-xz | script-zstd)");
		}
	}

	/** Dumps the DB to plain SQL, then compresses it with an external tool. */
	private Path scriptThenCompress(String base, String runId, String suffix, List<String> baseCmd) throws Exception {
		Path sql = Path.of(base + ".sql");
		Path compressed = Path.of(base + ".sql" + suffix);
		Path toolLog = Path.of(base + ".compress.log");
		try {
			long t = System.currentTimeMillis();
			jdbc.execute("SCRIPT TO '" + sql + "'");
			long rawSize = Files.size(sql);
			info("BACKUP", runId, "SCRIPT dump done size=" + human(rawSize) + " durationMs="
					+ (System.currentTimeMillis() - t) + " | compressing with " + baseCmd.get(0) + "...");

			List<String> cmd = new ArrayList<>(baseCmd);
			cmd.add(sql.toString());
			t = System.currentTimeMillis();
			Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(toolLog.toFile()).start();

			if (!p.waitFor(compressTimeoutMinutes, TimeUnit.MINUTES)) {
				p.destroyForcibly();
				throw new IllegalStateException(
						baseCmd.get(0) + " timed out after " + compressTimeoutMinutes + " minutes");
			}
			if (p.exitValue() != 0) {
				String msg = Files.exists(toolLog) ? Files.readString(toolLog) : "";
				throw new IllegalStateException(baseCmd.get(0) + " exit code " + p.exitValue() + ": " + msg);
			}

			long cSize = Files.size(compressed);
			info("BACKUP", runId,
					"COMPRESS done tool=" + baseCmd.get(0) + " " + human(rawSize) + " -> " + human(cSize) + " ("
							+ String.format("%.1f%%", cSize * 100.0 / Math.max(rawSize, 1)) + " of raw SQL) durationMs="
							+ (System.currentTimeMillis() - t));
			return compressed;
		} catch (Exception e) {
			// never leave half-written files that look like valid backups
			Files.deleteIfExists(compressed);
			throw e;
		} finally {
			Files.deleteIfExists(sql);
			Files.deleteIfExists(toolLog);
		}
	}

	private void deleteOldBackups(String runId) {
		deleteOldFiles("BACKUP", runId, backupDir, "codesync_", keepDays);
	}

	private void deleteOldArchives(String runId) {
		deleteOldFiles("ARCHIVE", runId, archiveDir, "codesync_archive_", archiveKeepDays);
	}

	@SuppressWarnings("unused")
	private void deleteOldFiles(String tag, String runId, String dir, String prefix, int days) {
		long cutoff = System.currentTimeMillis() - days * 86_400_000L;
		File[] files = new File(dir).listFiles((d, n) -> n.startsWith(prefix));
		int deleted = 0, kept = 0;
		long freed = 0;
		if (files != null) {
			for (File f : files) {
				if (f.lastModified() < cutoff) {
					long s = f.length();
					if (f.delete()) {
						deleted++;
						freed += s;
						info(tag, runId, "RETENTION deleted " + f.getName() + " (" + human(s) + ")");
					} else {
						warn(tag, runId, "RETENTION could not delete " + f.getAbsolutePath());
					}
				} else {
					kept++;
				}
			}
		}
		info(tag, runId,
				"RETENTION keepDays=" + days + " deleted=" + deleted + " freed=" + human(freed) + " kept=" + kept);
	}

	private record Result(long eligible, long copied, long deleted, long remaining, long durationMs) {
		@Override
		public String toString() {
			return "{eligible=" + eligible + ", copied=" + copied + ", deleted=" + deleted + ", remaining=" + remaining
					+ ", ms=" + durationMs + "}";
		}
	}

	/**
	 * Copies matching rows to the archive DB in batches, commits the archive, and
	 * only then deletes exactly those IDs from the main DB.
	 */
	@SuppressWarnings("unused")
	private Result archiveTable(String runId, Connection arch, String table, String archiveTable, String where,
			Object... params) throws Exception {
		long t0 = System.currentTimeMillis();
		long copied = 0, deleted = 0;
		int batchNo = 0;

		Long eligible = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, params);
		eligible = eligible == null ? 0 : eligible;
		info("ARCHIVE", runId, table + " eligible rows=" + eligible);

		arch.setAutoCommit(false);
		while (true) {
			List<Map<String, Object>> rows = jdbc.queryForList(
					"SELECT * FROM " + table + " WHERE " + where + " ORDER BY ID FETCH FIRST " + BATCH + " ROWS ONLY",
					params);
			if (rows.isEmpty())
				break;
			batchNo++;

			List<String> cols = List.copyOf(rows.get(0).keySet());
			String sql = "MERGE INTO " + archiveTable + " (" + String.join(",", cols) + ") KEY(ID) VALUES ("
					+ String.join(",", cols.stream().map(c -> "?").toList()) + ")";

			List<Object[]> ids = new ArrayList<>();
			try (var ps = arch.prepareStatement(sql)) {
				for (Map<String, Object> r : rows) {
					for (int i = 0; i < cols.size(); i++)
						ps.setObject(i + 1, r.get(cols.get(i)));
					ps.addBatch();
					ids.add(new Object[] { r.get("ID") });
				}
				ps.executeBatch();
				arch.commit(); // archive is safely written BEFORE we delete
			} catch (Exception e) {
				arch.rollback();
				error("ARCHIVE", runId, table + " batch #" + batchNo + " copy failed, rolled back archive batch. "
						+ "firstId=" + rows.get(0).get("ID") + " lastId=" + rows.get(rows.size() - 1).get("ID"), e);
				throw e;
			}
			copied += rows.size();

			int[] res = jdbc.batchUpdate("DELETE FROM " + table + " WHERE ID = ?", ids);
			long batchDeleted = 0;
			for (int r : res)
				batchDeleted += (r > 0) ? r : (r == java.sql.Statement.SUCCESS_NO_INFO ? 1 : 0);
			deleted += batchDeleted;

			if (batchDeleted != rows.size()) {
				warn("ARCHIVE", runId, table + " batch #" + batchNo + " copied=" + rows.size() + " but deleted="
						+ batchDeleted + " (rows may have been removed by the app meanwhile)");
			}
			CodeSyncLogger.logInfo("[ARCHIVE][" + runId + "] " + table + " batch #" + batchNo + " copied=" + rows.size()
					+ " deleted=" + batchDeleted + " totalCopied=" + copied);
		}

		Long remaining = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + where, Long.class, params);
		remaining = remaining == null ? 0 : remaining;

		Result result = new Result(eligible, copied, deleted, remaining, System.currentTimeMillis() - t0);
		if (remaining > 0) {
			warn("ARCHIVE", runId, table + " still has " + remaining
					+ " eligible rows after run (new rows may have qualified meanwhile)");
		}
		if (copied != deleted) {
			warn("ARCHIVE", runId, table + " MISMATCH copied=" + copied + " deleted=" + deleted
					+ " -> check manually; copied rows are safe in the archive (MERGE is idempotent, next run fixes it)");
		}
		info("ARCHIVE", runId, table + " finished " + result);
		return result;
	}

	private static String newRunId() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private static long fileSize(Path p) {
		try {
			return Files.exists(p) ? Files.size(p) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	private static String human(long bytes) {
		if (bytes < 1024)
			return bytes + " B";
		double v = bytes;
		String[] u = { "KB", "MB", "GB", "TB" };
		int i = -1;
		do {
			v /= 1024;
			i++;
		} while (v >= 1024 && i < u.length - 1);
		return String.format("%.2f %s", v, u[i]);
	}

	private void info(String tag, String runId, String msg) {
		CodeSyncLogger.logInfo("[" + tag + "][" + runId + "] " + msg);
	}

	private void warn(String tag, String runId, String msg) {
		CodeSyncLogger.logInfo("[" + tag + "][" + runId + "] WARN " + msg);
	}

	private void error(String tag, String runId, String msg, Exception e) {
		CodeSyncLogger.logError(getClass(), "[" + tag + "][" + runId + "] " + msg, e);
	}

	/**
	 * Derives the .mv.db path from spring.datasource.url (e.g.
	 * jdbc:h2:file:/opt/codesync/db/codesync;AUTO_SERVER=TRUE)
	 */
	private Path dbFile() {
		String p = dbUrl.replaceFirst("(?i)^jdbc:h2:(file:)?", "");
		int semi = p.indexOf(';');
		if (semi >= 0)
			p = p.substring(0, semi);
		if (p.startsWith("~"))
			p = System.getProperty("user.home") + p.substring(1);
		return Path.of(p + ".mv.db");
	}

	private static final String AUDIT_DDL = """
			CREATE TABLE IF NOT EXISTS CODE_SYNC_AUDIT_ARCHIVE (
			  ID BIGINT PRIMARY KEY,
			  HTTP_METHOD VARCHAR(255), URI VARCHAR(255), QUERY_STRING VARCHAR(255),
			  CLIENT_IP VARCHAR(255), STATUS_CODE INT, CONTENT_SIZE INT,
			  REQUEST_BODY CLOB, RESPONSE_BODY CLOB, DURATION_MS BIGINT,
			  CREATED_AT TIMESTAMP, FORWARDED_FOR VARCHAR(255), REAL_IP VARCHAR(255),
			  USER_AGENT VARCHAR(255), BROWSER_INFO VARCHAR(255), LANGUAGE VARCHAR(255),
			  REFERER VARCHAR(255), ORIGIN VARCHAR(255), HOST VARCHAR(255),
			  SEC_FETCH_SITE_MODE_DEST VARCHAR(255), SEC_CH_UA_PLATFORM_MOBILE VARCHAR(255),
			  UPLOADED_FILE_NAME VARCHAR(512), UPLOADED_FILE_SIZE BIGINT, ADDITIONAL_INFO CLOB
			)""";

	// FILE_ID is NOT unique here (the source table has the unique constraint)
	private static final String SHARED_FILE_DDL = """
			CREATE TABLE IF NOT EXISTS CODE_SYNC_SHARED_FILE_ARCHIVE (
			  ID BIGINT PRIMARY KEY,
			  SHARE_KEY VARCHAR(100), FILE_ID VARCHAR(64), ORIGINAL_NAME VARCHAR(512),
			  CONTENT_TYPE VARCHAR(128), FILE_SIZE BIGINT, STORED_PATH VARCHAR(1024),
			  UPLOADED_AT TIMESTAMP, DOWNLOAD_COUNT BIGINT, IS_ACTIVE BOOLEAN,
			  DELETED_AT TIMESTAMP, LAST_DOWNLOADED_AT TIMESTAMP,
			  UPLOADER_IP VARCHAR(64), UPLOADER_NAME VARCHAR(128), EXPIRES_AT TIMESTAMP,
			  IS_FILE_MOVED BOOLEAN, IS_FILE_DELETED_DIRECTLY BOOLEAN,
			  DELETED_BY_IP VARCHAR(64), DELETED_BY_NAME VARCHAR(128),
			  LAST_DOWNLOADED_BY_IP VARCHAR(64), LAST_DOWNLOADED_BY_NAME VARCHAR(128),
			  DOWNLOADERS VARCHAR(100000), DOWNLOADER_COUNT BIGINT
			)""";
}