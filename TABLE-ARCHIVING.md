	Here's the complete guide tailored to your exact tables.

---

## Step 1 — Create the Archive Tables

These mirror your exact entity structures plus metadata columns.

```sql
-- ============================================================
-- ARCHIVE TABLE FOR: CODE_SYNC_AUDIT
-- Mirrors CODE_SYNC_AUDIT exactly + adds archive tracking cols
-- ============================================================
CREATE TABLE CODE_SYNC_AUDIT_ARCHIVE LIKE CODE_SYNC_AUDIT;

-- Drop the sequence-based auto-increment from the clone (archive uses original IDs as-is)
ALTER TABLE CODE_SYNC_AUDIT_ARCHIVE
    MODIFY COLUMN id BIGINT NOT NULL,           -- keep original ID, no auto-generate
    ADD COLUMN archived_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN archive_reason VARCHAR(100)      DEFAULT 'scheduled_7day_cleanup';

-- ============================================================
-- ARCHIVE TABLE FOR: CODE_SYNC_SHARED_FILE
-- Mirrors CODE_SYNC_SHARED_FILE exactly + adds archive tracking cols
-- ============================================================
CREATE TABLE CODE_SYNC_SHARED_FILE_ARCHIVE LIKE CODE_SYNC_SHARED_FILE;

ALTER TABLE CODE_SYNC_SHARED_FILE_ARCHIVE
    MODIFY COLUMN id BIGINT NOT NULL,
    ADD COLUMN archived_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN archive_reason VARCHAR(100)      DEFAULT 'scheduled_7day_cleanup';
```

---

## Step 2 — Create an Archive Log Table

This is your monitoring table — every run gets logged here.

```sql
-- ============================================================
-- ARCHIVE RUN LOG TABLE
-- Every time the archive procedure runs, one row is inserted here.
-- Use this to monitor success/failure and row counts over time.
-- ============================================================
CREATE TABLE ARCHIVE_RUN_LOG (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    table_name      VARCHAR(100) NOT NULL,              -- which table was archived
    retention_days  INT          NOT NULL,              -- how many days of data were kept
    rows_archived   INT          NOT NULL DEFAULT 0,    -- rows copied to archive
    rows_deleted    INT          NOT NULL DEFAULT 0,    -- rows removed from main table
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',  -- SUCCESS or FAILED
    error_message   TEXT         NULL                   -- filled only on failure
);
```

---

## Step 3 — Create the Archive Stored Procedure

```sql
DELIMITER $$

-- ============================================================
-- STORED PROCEDURE: sp_archive_codesync_data
--
-- WHAT IT DOES:
--   1. Copies old rows from CODE_SYNC_AUDIT        → CODE_SYNC_AUDIT_ARCHIVE
--   2. Copies old rows from CODE_SYNC_SHARED_FILE  → CODE_SYNC_SHARED_FILE_ARCHIVE
--   3. Deletes those rows from the main tables
--   4. Logs the result into ARCHIVE_RUN_LOG
--
-- PARAMETER:
--   p_retention_days  INT  → rows older than this many days get archived
--                            e.g. CALL sp_archive_codesync_data(90);
--                            keeps last 90 days in main table
--
-- TO CHANGE RETENTION: just call with a different number, or edit the
-- event below (evt_weekly_archive) and change the argument value.
-- ============================================================
CREATE PROCEDURE sp_archive_codesync_data(IN p_retention_days INT)
BEGIN

    -- ---- Local variables to track counts ----
    DECLARE v_audit_archived   INT DEFAULT 0;
    DECLARE v_audit_deleted    INT DEFAULT 0;
    DECLARE v_shared_archived  INT DEFAULT 0;
    DECLARE v_shared_deleted   INT DEFAULT 0;

    -- ---- Error handler: on any SQL error, rollback and log failure ----
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;

        -- Log the failure with the error message
        GET DIAGNOSTICS CONDITION 1 @err_msg = MESSAGE_TEXT;

        INSERT INTO ARCHIVE_RUN_LOG (table_name, retention_days, rows_archived, rows_deleted, status, error_message)
        VALUES ('ALL_TABLES', p_retention_days, 0, 0, 'FAILED', @err_msg);

        -- Re-surface the error so the event scheduler captures it too
        RESIGNAL;
    END;

    -- ==============================================================
    -- TRANSACTION START
    -- Both tables are handled in one transaction.
    -- If anything fails, BOTH inserts and deletes are rolled back.
    -- ==============================================================
    START TRANSACTION;

        -- ----------------------------------------------------------
        -- BLOCK 1: Archive CODE_SYNC_AUDIT
        --
        -- CHANGE THE CONDITION HERE if you want different logic, e.g.:
        --   WHERE created_at < NOW() - INTERVAL p_retention_days DAY
        --   AND status_code >= 500          ← archive only errors
        --   AND uri NOT LIKE '/health%'     ← skip health-check calls
        -- ----------------------------------------------------------
        INSERT INTO CODE_SYNC_AUDIT_ARCHIVE
        SELECT
            id, http_method, uri, query_string, client_ip,
            status_code, content_size, request_body, duration_ms,
            created_at, forwarded_for, real_ip, user_agent,
            browser_info, language, referer, origin, host,
            sec_fetch_site_mode_dest, sec_ch_ua_platform_mobile,
            uploaded_file_name, uploaded_file_size, additional_info,
            NOW(),                      -- archived_at
            'scheduled_7day_cleanup'    -- archive_reason  ← change label here if needed
        FROM CODE_SYNC_AUDIT
        WHERE created_at < NOW() - INTERVAL p_retention_days DAY;

        SET v_audit_archived = ROW_COUNT();

        DELETE FROM CODE_SYNC_AUDIT
        WHERE created_at < NOW() - INTERVAL p_retention_days DAY;

        SET v_audit_deleted = ROW_COUNT();

        -- ----------------------------------------------------------
        -- BLOCK 2: Archive CODE_SYNC_SHARED_FILE
        --
        -- CHANGE THE CONDITION HERE if you want different logic, e.g.:
        --   WHERE uploaded_at < NOW() - INTERVAL p_retention_days DAY
        --   AND is_active = FALSE        ← only archive inactive/deleted files
        --   AND is_file_moved = TRUE     ← only archive already-moved files
        -- ----------------------------------------------------------
        INSERT INTO CODE_SYNC_SHARED_FILE_ARCHIVE
        SELECT
            id, share_key, file_id, original_name, content_type,
            file_size, stored_path, uploaded_at, download_count,
            is_active, deleted_at, last_downloaded_at,
            uploader_ip, uploader_name, expires_at, is_file_moved,
            NOW(),                      -- archived_at
            'scheduled_7day_cleanup'    -- archive_reason  ← change label here if needed
        FROM CODE_SYNC_SHARED_FILE
        WHERE uploaded_at < NOW() - INTERVAL p_retention_days DAY;

        SET v_shared_archived = ROW_COUNT();

        DELETE FROM CODE_SYNC_SHARED_FILE
        WHERE uploaded_at < NOW() - INTERVAL p_retention_days DAY;

        SET v_shared_deleted = ROW_COUNT();

    COMMIT;
    -- ==============================================================
    -- TRANSACTION END
    -- ==============================================================

    -- ----------------------------------------------------------
    -- Log results for both tables into ARCHIVE_RUN_LOG
    -- ----------------------------------------------------------
    INSERT INTO ARCHIVE_RUN_LOG (table_name, retention_days, rows_archived, rows_deleted, status)
    VALUES ('CODE_SYNC_AUDIT', p_retention_days, v_audit_archived, v_audit_deleted, 'SUCCESS');

    INSERT INTO ARCHIVE_RUN_LOG (table_name, retention_days, rows_archived, rows_deleted, status)
    VALUES ('CODE_SYNC_SHARED_FILE', p_retention_days, v_shared_archived, v_shared_deleted, 'SUCCESS');

    -- Show a summary in console when called manually
    SELECT
        'CODE_SYNC_AUDIT'           AS table_name,
        v_audit_archived            AS rows_archived,
        v_audit_deleted             AS rows_deleted,
        p_retention_days            AS retention_days_kept
    UNION ALL
    SELECT
        'CODE_SYNC_SHARED_FILE',
        v_shared_archived,
        v_shared_deleted,
        p_retention_days;

END$$

DELIMITER ;
```

---

## Step 4 — Enable the Event Scheduler & Schedule It

```sql
-- ============================================================
-- Enable MySQL Event Scheduler (must be ON for events to run)
-- Check current status first:
-- ============================================================
SHOW VARIABLES LIKE 'event_scheduler';

-- If it shows OFF, enable it:
SET GLOBAL event_scheduler = ON;

-- To make it permanent across MySQL restarts,
-- add this to your /etc/mysql/my.cnf or my.ini:
--   [mysqld]
--   event_scheduler = ON


-- ============================================================
-- SCHEDULED EVENT: evt_weekly_archive
--
-- SCHEDULE: Every Monday at 11:00 AM
--   EVERY 1 WEEK         ← change to EVERY 1 DAY or EVERY 1 MONTH if needed
--   STARTS '...' Monday  ← first run anchor (must be a Monday)
--
-- ARGUMENT: 90  ← retention in days
--   Change 90 to any number of days you want to retain in main table
--   e.g. 30 = keep only last 30 days, 180 = keep last 6 months
-- ============================================================
CREATE EVENT evt_weekly_archive
    ON SCHEDULE
        EVERY 1 WEEK
        STARTS '2026-06-15 11:00:00'   -- ← This is a Monday. Change to next upcoming Monday.
    DO
        CALL sp_archive_codesync_data(15);   -- ← Change 90 to your desired retention days
```

> **Note:** Change `2025-06-16` to the next upcoming Monday's date when you run this. The event will repeat every 7 days from that start point automatically.

---

## Step 5 — Test Manually Before Relying on the Schedule

```sql
-- Run the archive manually right now (safe to test with a high number first)
CALL sp_archive_codesync_data(9999);   -- 9999 days = archives almost nothing, safe test

-- Then check the log
SELECT * FROM ARCHIVE_RUN_LOG ORDER BY run_at DESC LIMIT 10;

-- When confident, test with your real retention window
CALL sp_archive_codesync_data(90);
```

---

## Step 6 — Monitoring Queries

```sql
-- ============================================================
-- 1. CHECK: All archive runs — latest first
-- ============================================================
SELECT * FROM ARCHIVE_RUN_LOG ORDER BY run_at DESC LIMIT 20;


-- ============================================================
-- 2. CHECK: Did last Monday's job actually run?
-- ============================================================
SELECT *
FROM ARCHIVE_RUN_LOG
WHERE run_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY run_at DESC;


-- ============================================================
-- 3. CHECK: Any failures?
-- ============================================================
SELECT * FROM ARCHIVE_RUN_LOG WHERE status = 'FAILED' ORDER BY run_at DESC;


-- ============================================================
-- 4. CHECK: How many rows are in main vs archive tables
-- ============================================================
SELECT 'CODE_SYNC_AUDIT (main)'        AS tbl, COUNT(*) AS row_count FROM CODE_SYNC_AUDIT
UNION ALL
SELECT 'CODE_SYNC_AUDIT (archive)',            COUNT(*) FROM CODE_SYNC_AUDIT_ARCHIVE
UNION ALL
SELECT 'CODE_SYNC_SHARED_FILE (main)',         COUNT(*) FROM CODE_SYNC_SHARED_FILE
UNION ALL
SELECT 'CODE_SYNC_SHARED_FILE (archive)',      COUNT(*) FROM CODE_SYNC_SHARED_FILE_ARCHIVE;


-- ============================================================
-- 5. CHECK: Total rows archived per week (trend view)
-- ============================================================
SELECT
    DATE(run_at)            AS run_date,
    table_name,
    SUM(rows_archived)      AS total_archived,
    SUM(rows_deleted)       AS total_deleted
FROM ARCHIVE_RUN_LOG
WHERE status = 'SUCCESS'
GROUP BY DATE(run_at), table_name
ORDER BY run_date DESC;


-- ============================================================
-- 6. CHECK: Is the event scheduler running?
-- ============================================================
SHOW VARIABLES LIKE 'event_scheduler';
SHOW EVENTS FROM codesync;          -- lists your scheduled event and its next run time
```

---

## Quick Reference — Things You'll Want to Change Later

| What to change | Where |
|---|---|
| Retention window (days) | In the `CREATE EVENT` — change `90` in `CALL sp_archive_codesync_data(90)` |
| Schedule frequency | In the `CREATE EVENT` — change `EVERY 1 WEEK` to `EVERY 1 DAY` etc. |
| Archive condition for AUDIT | In the procedure `BLOCK 1` — modify the `WHERE` clause |
| Archive condition for SHARED_FILE | In the procedure `BLOCK 2` — modify the `WHERE` clause |
| Archive reason label | Change `'scheduled_7day_cleanup'` string in both INSERT blocks |
| Disable the event temporarily | `ALTER EVENT evt_weekly_archive DISABLE;` |
| Re-enable the event | `ALTER EVENT evt_weekly_archive ENABLE;` |
| Drop and recreate with new schedule | `DROP EVENT evt_weekly_archive;` then re-run `CREATE EVENT` |
















































CLAUDE RESPONSE








Looking at your actual table DDLs, I can spot a few issues with the original code:

**Problems I found:**
1. `CREATE TABLE ... LIKE` won't work cleanly — your source tables use `bigint(20) NOT NULL` for `id` with no `AUTO_INCREMENT`, so the clone is fine, but the `MODIFY COLUMN` is redundant and risky
2. The `SELECT *` in the INSERT was **column-order dependent** — if your table ever gets a new column or columns are reordered, it silently inserts wrong data. I'll use **explicit column mapping** instead
3. `REQUEST_BODY` is uppercase in your DDL — the original code used `request_body` (lowercase), which could fail on case-sensitive systems
4. The archive tables need `archived_at` and `archive_reason` added **after** creation, which `CREATE TABLE ... LIKE` won't include
5. Added `IF EXISTS` on all drops so re-running is safe

**What you need to do on your end:**
- Make sure `event_scheduler = ON` is set in your `my.cnf`/`my.ini` permanently (not just `SET GLOBAL` which resets on restart)
- Change the `STARTS` date in the event to the **next upcoming Monday**
- Confirm your MySQL user has `EVENT` privilege: `GRANT EVENT ON codesync.* TO 'your_user'@'%';`

Here's the complete final script:

```sql
-- ============================================================
-- STEP 0: CLEANUP — Drop everything old first
-- Run this section first before creating anything new
-- ============================================================

-- Drop the scheduled event
DROP EVENT IF EXISTS evt_weekly_archive;

-- Drop the stored procedure
DROP PROCEDURE IF EXISTS sp_archive_codesync_data;

-- Drop archive tables (data will be lost — confirm before running)
DROP TABLE IF EXISTS CODE_SYNC_AUDIT_ARCHIVE;
DROP TABLE IF EXISTS CODE_SYNC_SHARED_FILE_ARCHIVE;

-- Drop the run log (optional — remove this line if you want to keep history)
DROP TABLE IF EXISTS ARCHIVE_RUN_LOG;


-- ============================================================
-- STEP 1: CREATE ARCHIVE TABLE FOR CODE_SYNC_AUDIT
-- Explicit DDL (not LIKE) so we control exactly what gets created
-- ============================================================
CREATE TABLE CODE_SYNC_AUDIT_ARCHIVE (
    -- All original columns (exact types from your DDL)
    id                          BIGINT(20)      NOT NULL,           -- original ID, no auto_increment
    browser_info                VARCHAR(255)    DEFAULT NULL,
    client_ip                   VARCHAR(255)    DEFAULT NULL,
    content_size                INT(11)         DEFAULT NULL,
    created_at                  DATETIME(6)     DEFAULT NULL,
    duration_ms                 BIGINT(20)      DEFAULT NULL,
    forwarded_for               VARCHAR(255)    DEFAULT NULL,
    host                        VARCHAR(255)    DEFAULT NULL,
    http_method                 VARCHAR(255)    DEFAULT NULL,
    language                    VARCHAR(255)    DEFAULT NULL,
    origin                      VARCHAR(255)    DEFAULT NULL,
    query_string                VARCHAR(255)    DEFAULT NULL,
    real_ip                     VARCHAR(255)    DEFAULT NULL,
    referer                     VARCHAR(255)    DEFAULT NULL,
    REQUEST_BODY                LONGTEXT        DEFAULT NULL,       -- uppercase as in your DDL
    sec_ch_ua_platform_mobile   VARCHAR(255)    DEFAULT NULL,
    sec_fetch_site_mode_dest    VARCHAR(255)    DEFAULT NULL,
    status_code                 INT(11)         DEFAULT NULL,
    uploaded_file_name          VARCHAR(512)    DEFAULT NULL,
    uploaded_file_size          BIGINT(20)      DEFAULT NULL,
    uri                         VARCHAR(255)    DEFAULT NULL,
    user_agent                  VARCHAR(255)    DEFAULT NULL,
    additional_info             LONGTEXT        DEFAULT NULL,

    -- Archive tracking columns
    archived_at                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archive_reason              VARCHAR(100)    DEFAULT 'scheduled_cleanup',

    -- Archive table primary key (auto-increment, separate from original id)
    archive_id                  BIGINT          NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (archive_id),

    -- Index on original id for lookups
    KEY IDX_ARCH_AUDIT_ORIG_ID  (id),
    -- Index for querying by original created_at range
    KEY IDX_ARCH_AUDIT_CREATED  (created_at),
    -- Index to filter by when it was archived
    KEY IDX_ARCH_AUDIT_ARCH_AT  (archived_at)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ============================================================
-- STEP 2: CREATE ARCHIVE TABLE FOR CODE_SYNC_SHARED_FILE
-- ============================================================
CREATE TABLE CODE_SYNC_SHARED_FILE_ARCHIVE (
    -- All original columns (exact types from your DDL)
    id                  BIGINT(20)      NOT NULL,
    content_type        VARCHAR(128)    DEFAULT NULL,
    deleted_at          DATETIME(6)     DEFAULT NULL,
    download_count      BIGINT(20)      NOT NULL,
    expires_at          DATETIME(6)     DEFAULT NULL,
    file_id             VARCHAR(64)     NOT NULL,
    file_size           BIGINT(20)      DEFAULT NULL,
    is_active           BIT(1)          NOT NULL,
    last_downloaded_at  DATETIME(6)     DEFAULT NULL,
    original_name       VARCHAR(512)    NOT NULL,
    share_key           VARCHAR(100)    NOT NULL,
    stored_path         VARCHAR(1024)   NOT NULL,
    uploaded_at         DATETIME(6)     DEFAULT NULL,
    uploader_ip         VARCHAR(64)     DEFAULT NULL,
    uploader_name       VARCHAR(128)    DEFAULT NULL,
    is_file_moved       BIT(1)          NOT NULL,

    -- Archive tracking columns
    archived_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archive_reason      VARCHAR(100)    DEFAULT 'scheduled_cleanup',

    -- Archive table primary key
    archive_id          BIGINT          NOT NULL AUTO_INCREMENT,
    PRIMARY KEY (archive_id),

    -- Index on original id for lookups
    KEY IDX_ARCH_SF_ORIG_ID     (id),
    -- Index for querying by upload date
    KEY IDX_ARCH_SF_UPLOADED    (uploaded_at),
    -- Index for querying by archived date
    KEY IDX_ARCH_SF_ARCH_AT     (archived_at),
    -- Index on file_id (was UNIQUE in source — NOT unique here since same file could
    -- theoretically be archived twice if procedure is run twice with overlapping windows)
    KEY IDX_ARCH_SF_FILE_ID     (file_id)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ============================================================
-- STEP 3: CREATE ARCHIVE RUN LOG TABLE
-- ============================================================
CREATE TABLE ARCHIVE_RUN_LOG (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    run_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    table_name      VARCHAR(100)    NOT NULL,
    retention_days  INT             NOT NULL,
    rows_archived   INT             NOT NULL DEFAULT 0,
    rows_deleted    INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT            NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


-- ============================================================
-- STEP 4: CREATE THE STORED PROCEDURE USE alt+x
-- ============================================================
DELIMITER $$

CREATE PROCEDURE sp_archive_codesync_data(IN p_retention_days INT)
BEGIN

    -- ALL DECLARES MUST BE FIRST (MariaDB rule)
    DECLARE v_audit_archived   INT DEFAULT 0;
    DECLARE v_audit_deleted    INT DEFAULT 0;
    DECLARE v_shared_archived  INT DEFAULT 0;
    DECLARE v_shared_deleted   INT DEFAULT 0;
    DECLARE v_cutoff_audit     DATETIME(6);
    DECLARE v_cutoff_shared    DATETIME(6);

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        GET DIAGNOSTICS CONDITION 1 @err_msg = MESSAGE_TEXT;
        INSERT INTO ARCHIVE_RUN_LOG
            (table_name, retention_days, rows_archived, rows_deleted, status, error_message)
        VALUES
            ('ALL_TABLES', p_retention_days, 0, 0, 'FAILED', @err_msg);
        RESIGNAL;
    END;

    -- SET statements come AFTER all DECLAREs
    SET v_cutoff_audit  = NOW() - INTERVAL p_retention_days DAY;
    SET v_cutoff_shared = NOW() - INTERVAL p_retention_days DAY;

    START TRANSACTION;

        INSERT INTO CODE_SYNC_AUDIT_ARCHIVE (
            id, browser_info, client_ip, content_size, created_at, duration_ms,
            forwarded_for, host, http_method, language, origin, query_string,
            real_ip, referer, REQUEST_BODY, sec_ch_ua_platform_mobile,
            sec_fetch_site_mode_dest, status_code, uploaded_file_name,
            uploaded_file_size, uri, user_agent, additional_info,
            archived_at, archive_reason
        )
        SELECT
            id, browser_info, client_ip, content_size, created_at, duration_ms,
            forwarded_for, host, http_method, language, origin, query_string,
            real_ip, referer, REQUEST_BODY, sec_ch_ua_platform_mobile,
            sec_fetch_site_mode_dest, status_code, uploaded_file_name,
            uploaded_file_size, uri, user_agent, additional_info,
            NOW(), 'scheduled_cleanup'
        FROM CODE_SYNC_AUDIT
        WHERE created_at < v_cutoff_audit;

        SET v_audit_archived = ROW_COUNT();

        DELETE FROM CODE_SYNC_AUDIT
        WHERE created_at < v_cutoff_audit;

        SET v_audit_deleted = ROW_COUNT();

        IF v_audit_archived <> v_audit_deleted THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'AUDIT mismatch: archived count != deleted count. Rolling back.';
        END IF;

        INSERT INTO CODE_SYNC_SHARED_FILE_ARCHIVE (
            id, content_type, deleted_at, download_count, expires_at, file_id,
            file_size, is_active, last_downloaded_at, original_name, share_key,
            stored_path, uploaded_at, uploader_ip, uploader_name, is_file_moved,
            archived_at, archive_reason
        )
        SELECT
            id, content_type, deleted_at, download_count, expires_at, file_id,
            file_size, is_active, last_downloaded_at, original_name, share_key,
            stored_path, uploaded_at, uploader_ip, uploader_name, is_file_moved,
            NOW(), 'scheduled_cleanup'
        FROM CODE_SYNC_SHARED_FILE
        WHERE uploaded_at < v_cutoff_shared;

        SET v_shared_archived = ROW_COUNT();

        DELETE FROM CODE_SYNC_SHARED_FILE
        WHERE uploaded_at < v_cutoff_shared;

        SET v_shared_deleted = ROW_COUNT();

        IF v_shared_archived <> v_shared_deleted THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'SHARED_FILE mismatch: archived count != deleted count. Rolling back.';
        END IF;

    COMMIT;

    INSERT INTO ARCHIVE_RUN_LOG
        (table_name, retention_days, rows_archived, rows_deleted, status)
    VALUES
        ('CODE_SYNC_AUDIT', p_retention_days, v_audit_archived, v_audit_deleted, 'SUCCESS');

    INSERT INTO ARCHIVE_RUN_LOG
        (table_name, retention_days, rows_archived, rows_deleted, status)
    VALUES
        ('CODE_SYNC_SHARED_FILE', p_retention_days, v_shared_archived, v_shared_deleted, 'SUCCESS');

    SELECT
        'CODE_SYNC_AUDIT'       AS table_name,
        v_audit_archived        AS rows_archived,
        v_audit_deleted         AS rows_deleted,
        p_retention_days        AS retention_days_kept
    UNION ALL
    SELECT
        'CODE_SYNC_SHARED_FILE',
        v_shared_archived,
        v_shared_deleted,
        p_retention_days;

END$$

DELIMITER ;


-- ============================================================
-- STEP 5: ENABLE EVENT SCHEDULER & CREATE SCHEDULED EVENT
--
-- Check current status:
--   SHOW VARIABLES LIKE 'event_scheduler';
-- If OFF, enable for this session:
--   SET GLOBAL event_scheduler = ON;
-- To make permanent, add to /etc/mysql/my.cnf under [mysqld]:
--   event_scheduler = ON
-- ============================================================
SET GLOBAL event_scheduler = ON;

-- !! IMPORTANT: Change the STARTS date to the next upcoming Monday !!
-- Current placeholder: 2026-06-29 (next Monday from today June 22 2026)
CREATE EVENT evt_weekly_archive
    ON SCHEDULE
        EVERY 1 WEEK
        STARTS '2026-06-29 11:00:00'    -- ← next Monday, change if needed
    ON COMPLETION PRESERVE              -- keeps the event after it finishes (important!)
    ENABLE
    DO
        CALL sp_archive_codesync_data(7);  -- ← 7 = keep last 7 days in main table


-- ============================================================
-- STEP 6: VERIFY EVERYTHING IS SET UP CORRECTLY
-- ============================================================

-- Confirm tables exist
SHOW TABLES LIKE '%ARCHIVE%';
SHOW TABLES LIKE 'ARCHIVE_RUN_LOG';

-- Confirm procedure exists
SHOW PROCEDURE STATUS WHERE Name = 'sp_archive_codesync_data';

-- Confirm event is scheduled (replace 'codesync' with your actual DB name)
SHOW EVENTS FROM codesync;

-- Confirm event scheduler is ON
SHOW VARIABLES LIKE 'event_scheduler';


-- ============================================================
-- STEP 7: TEST MANUALLY (run this before trusting the schedule)
-- ============================================================

-- Safe dry-run: 9999 days means almost nothing qualifies, so nothing gets deleted
CALL sp_archive_codesync_data(9999);

-- Check the log — should show 2 SUCCESS rows with 0 counts
SELECT * FROM ARCHIVE_RUN_LOG ORDER BY run_at DESC LIMIT 10;

-- Row count sanity check across all 4 tables
SELECT 'CODE_SYNC_AUDIT (main)'             AS tbl, COUNT(*) AS row_count FROM CODE_SYNC_AUDIT
UNION ALL
SELECT 'CODE_SYNC_AUDIT (archive)',                  COUNT(*) FROM CODE_SYNC_AUDIT_ARCHIVE
UNION ALL
SELECT 'CODE_SYNC_SHARED_FILE (main)',               COUNT(*) FROM CODE_SYNC_SHARED_FILE
UNION ALL
SELECT 'CODE_SYNC_SHARED_FILE (archive)',            COUNT(*) FROM CODE_SYNC_SHARED_FILE_ARCHIVE;

-- When confident, run with real retention window:
-- CALL sp_archive_codesync_data(15);
```

**Summary of all fixes made:**

| Fix | Why |
|---|---|
| Explicit `CREATE TABLE` DDL instead of `CREATE TABLE ... LIKE` | Full control, no surprises from column order or constraints carrying over |
| Separate `archive_id AUTO_INCREMENT` PK | Original `id` is kept as-is; archive table has its own PK so duplicate runs don't conflict |
| Pre-computed cutoff timestamps (`v_cutoff_audit`, `v_cutoff_shared`) | INSERT and DELETE use the exact same cutoff — no risk of deleting rows that weren't archived |
| Explicit column list on INSERT...SELECT | Safe against schema changes, column reordering, and case mismatches |
| `REQUEST_BODY` uppercase preserved | Matches your actual DDL exactly |
| Archived/deleted count mismatch check (`IF ... SIGNAL`) | Catches silent partial failures and rolls back instead of deleting un-archived rows |
| `ON COMPLETION PRESERVE` on the event | Without this, MySQL drops the event after it runs once |
| Next Monday's date pre-filled (`2026-06-29`) | Ready to paste, no guessing needed |






























FOR H2 DB ARCHIVING CLAUDE
Yes, both are possible in H2, and I'd do both. They solve different problems:

- **Backup (`BACKUP TO`)** gives you a timestamped, restorable zip of the whole DB.
- **Archive** moves old audit rows into a separate H2 file, so the main DB stops growing. Archiving into the same file wouldn't shrink anything, so use a separate file.

Your growth problem is almost certainly `CODE_SYNC_AUDIT`, because `REQUEST_BODY` and `RESPONSE_BODY` are CLOBs. Don't archive `CODE_SYNC` or `CODE_SYNC_SHARED_FILE` this way, since the app still needs those rows to serve shares and downloads.

## 1. Enable scheduling

Add `@EnableScheduling` to your main class or any `@Configuration` class.

## 2. Properties

```properties
codesync.backup.dir=/opt/codesync/backup
codesync.backup.cron=0 0 2 * * *
codesync.backup.keep-days=30

codesync.archive.dir=/opt/codesync/archive
codesync.archive.cron=0 30 2 * * *
codesync.archive.retention-days=7
```

## 3. The service

```java
package com.cs.service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.cs.config.CodeSyncLogger;

@Service
public class DbMaintenanceService {

	private static final int BATCH = 500;

	private final JdbcTemplate jdbc;

	@Value("${codesync.backup.dir}")
	private String backupDir;
	@Value("${codesync.backup.keep-days:30}")
	private int keepDays;
	@Value("${codesync.archive.dir}")
	private String archiveDir;
	@Value("${codesync.archive.retention-days:7}")
	private int retentionDays;
	@Value("${spring.datasource.username}")
	private String dbUser;
	@Value("${spring.datasource.password:}")
	private String dbPass;

	public DbMaintenanceService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/* ---------- FULL BACKUP (hot, DB stays online) ---------- */
	@Scheduled(cron = "${codesync.backup.cron}")
	public void backup() {
		try {
			new File(backupDir).mkdirs();
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			String file = backupDir + "/codesync_" + ts + ".zip";
			jdbc.execute("BACKUP TO '" + file + "'");
			CodeSyncLogger.logInfo("DB backup created: " + file);

			// delete old backups
			long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
			File[] old = new File(backupDir).listFiles((d, n) -> n.startsWith("codesync_") && n.endsWith(".zip"));
			if (old != null)
				for (File f : old)
					if (f.lastModified() < cutoff)
						f.delete();
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Backup failed", e);
		}
	}

	/* ---------- ARCHIVE OLD AUDIT ROWS TO A SEPARATE H2 FILE ---------- */
	@Scheduled(cron = "${codesync.archive.cron}")
	public void archiveAudit() {
		Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(retentionDays));
		new File(archiveDir).mkdirs();
		// one archive file per month
		String url = "jdbc:h2:file:" + archiveDir + "/codesync_archive_" + LocalDate.now() + ";AUTO_SERVER=TRUE";

		try (Connection arch = DriverManager.getConnection(url, dbUser, dbPass)) {
			try (Statement st = arch.createStatement()) {
				st.execute(ARCHIVE_DDL);
				st.execute("CREATE INDEX IF NOT EXISTS IDX_ARCH_AUDIT_CREATED ON CODE_SYNC_AUDIT_ARCHIVE(CREATED_AT)");
			}

			int total = 0;
			while (true) {
				List<Map<String, Object>> rows = jdbc.queryForList(
						"SELECT * FROM CODE_SYNC_AUDIT WHERE CREATED_AT < ? ORDER BY ID FETCH FIRST " + BATCH
								+ " ROWS ONLY",
						cutoff);
				if (rows.isEmpty())
					break;

				List<String> cols = List.copyOf(rows.get(0).keySet());
				String sql = "MERGE INTO CODE_SYNC_AUDIT_ARCHIVE (" + String.join(",", cols) + ") KEY(ID) VALUES ("
						+ String.join(",", cols.stream().map(c -> "?").toList()) + ")";

				arch.setAutoCommit(false);
				try (var ps = arch.prepareStatement(sql)) {
					long maxId = 0;
					for (Map<String, Object> r : rows) {
						for (int i = 0; i < cols.size(); i++)
							ps.setObject(i + 1, r.get(cols.get(i)));
						ps.addBatch();
						maxId = Math.max(maxId, ((Number) r.get("ID")).longValue());
					}
					ps.executeBatch();
					arch.commit(); // archive is safely written BEFORE we delete

					jdbc.update("DELETE FROM CODE_SYNC_AUDIT WHERE ID <= ? AND CREATED_AT < ?", maxId, cutoff);
					total += rows.size();
				}
			}
			CodeSyncLogger.logInfo("Audit archive done, rows moved: " + total + " -> " + url);
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Archive failed", e);
		}
	}

	private static final String ARCHIVE_DDL = """
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
}
```

**Why the archive is safe to re-run:**
- It uses `MERGE ... KEY(ID)`, so if the app crashes after copying but before deleting, the next run simply overwrites the same rows instead of duplicating them.
- The archive is committed before anything is deleted.
- It works in 500-row batches, so memory stays small.

If you later add columns to `CodeSyncAudit`, add them to `ARCHIVE_DDL` too. The copy picks up columns dynamically, so the insert will fail if the archive table lacks one.

## 4. Opening a backup or archive later

- **Archive file** (`codesync_archive_2026-10.mv.db`): in your H2 console use `jdbc:h2:file:/opt/codesync/archive/codesync_archive_2026-10;AUTO_SERVER=TRUE`, with the same username and an empty password. Leave off the `.mv.db` extension.
- **Backup zip**: unzip it somewhere (e.g. `/tmp/restore/`) and open the `.mv.db` file inside the same way, again without the extension. To restore, stop the app and replace `/opt/codesync/db/codesync.mv.db` with it.

## Things to know

1. **The main file won't shrink right after deleting rows.** H2 reuses the freed space, so growth stops, but the file size stays. To actually shrink it, run `SHUTDOWN COMPACT` once in a maintenance window, or restore from a `SCRIPT` export.
2. **`BACKUP TO` needs an admin user.** In H2 the first user created in the DB (`code_sync`) is normally admin, so it should work as is.
3. **Version-proof alternative:** if you want backups that survive H2 upgrades, replace `BACKUP TO '...zip'` with `SCRIPT TO '/opt/codesync/backup/x.sql.zip' COMPRESSION ZIP`. It's slower and larger but produces plain SQL you can run anywhere.
4. **Backup location:** keep `backupDir` on a different disk or machine if you can. A backup on the same disk won't help if the disk dies.
5. **Per-day archives:** if you'd rather have one archive file per day, change `YearMonth.now()` to `LocalDate.now()`.

I haven't run this against your setup. Try it first with a small `retention-days` on a copy of the DB, and trigger `archiveAudit()` manually to check the row counts before trusting the cron.

Yes, two separate things, because they solve different problems.

| | Backup zip | Archive file |
|---|---|---|
| **Purpose** | Disaster recovery: restore the app if the DB is lost or corrupted | Keep the main DB small by moving old logs out |
| **Contents** | The entire database | Only `CODE_SYNC_AUDIT` rows older than 7 days |
| **Main DB afterwards** | Unchanged, it only copies | Those rows are deleted from it |
| **Frequency** | Daily snapshot, old zips deleted after 30 days | One file per month, kept as long as you want |

## What the zip includes

Everything in the database at that moment: all tables (`CODE_SYNC`, `CODE_SYNC_AUDIT`, `CODE_SYNC_SHARED_FILE`, `CODE_SYNC_BLOCKED_IPS`, `CODE_SYNC_CLIENTS`), indexes, sequences, and DB users. It is basically a compressed copy of `codesync.mv.db`.

It does **not** include the actual uploaded files stored on disk (the paths in `STORED_PATH`). The DB only holds their metadata, so back up that folder separately if you need those.

## What the archive includes

Only old audit log rows (request details, request/response bodies, IPs, and so on). Nothing from the other tables.

## How they interact

Once a row is archived, it is deleted from the main DB, so **backups taken after that no longer contain it**. Old audit logs then exist only in the archive file, and in older zips taken before they were moved. That means:

- To investigate old traffic, open the archive file.
- To recover from a crash, restore the latest zip, which has the app data plus the last 7 days of audit logs.
- Back up the archive folder too (copy it to another disk or machine), since it is the only copy of those old logs.

## If you want just one

- **Only archive:** the main file stops growing and old logs stay searchable, but there's no protection if the DB gets corrupted or the disk fails.
- **Only backup:** you're protected against loss, but the main DB keeps growing, because the audit table is never trimmed.

My recommendation is both, since the audit table is what grows and the other tables are what you can't afford to lose. If you'd rather keep it simpler, I can show a version that only trims audit rows into the archive and backs up the archive folder with a plain file copy.

I've updated `DbMaintenanceScheduler.java` so it now archives `CODE_SYNC_SHARED_FILE` as well as `CODE_SYNC_AUDIT`. I haven't compiled or run it, so test it on a copy of your DB first.

**Active shares are not archived.** Only rows the app no longer needs are moved: `UPLOADED_AT` older than the retention period **and** (`IS_ACTIVE = FALSE` **or** `EXPIRES_AT` already passed). If I archived active rows too, their share links and downloads would stop working, because the app looks them up in the main table.

**What changed**
- Both tables now go through one shared `archiveTable(...)` method, and they use the same archive file for that day.
- The archive table for shared files has all 17 columns, including `IS_FILE_DELETED_DIRECTLY`. If you add columns to the entity later, add them to the DDL too.
- `FILE_ID` is not unique in the archive table.
- Rows are now deleted from the main table by their exact IDs after the archive commit, instead of `ID <= maxId`. This is safer if a row changes state while the job runs.
- The audit and shared-file jobs are wrapped separately, so a failure in one doesn't stop the other.
- I added `@Component` to the class. Your pasted version didn't have it, so the schedules would never have run.
- The method is renamed to `archiveOldData()`, so use that name if you trigger it manually.

**Add this property** (the default is 30 days if you leave it out):
```properties
codesync.archive.shared-file-retention-days=30
```

**Two things to check**
1. Your code comment says "one file per month", but the URL uses `LocalDate.now()`, which gives one archive file per day. I kept the per-day naming. If you want monthly files, switch it to `YearMonth.now()` and add the import.
2. Archiving only copies the database rows. The actual files at `STORED_PATH` stay on disk and are not touched. If your app deletes them when a share expires, the archive row will point to a file that no longer exists, which is fine for a record of what was shared. If you want the files kept, you'd need to copy them separately.

package com.cs.scheduler;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cs.config.CodeSyncLogger;

@Component
public class DbMaintenanceScheduler {

	private static final int BATCH = 500;

	private final JdbcTemplate jdbc;

	@Value("${codesync.backup.dir}")
	private String backupDir;
	@Value("${codesync.backup.keep-days:30}")
	private int keepDays;
	@Value("${codesync.archive.dir}")
	private String archiveDir;
	@Value("${codesync.archive.retention-days:7}")
	private int auditRetentionDays;
	@Value("${codesync.archive.shared-file-retention-days:30}")
	private int sharedFileRetentionDays;
	@Value("${spring.datasource.username}")
	private String dbUser;
	@Value("${spring.datasource.password:}")
	private String dbPass;

	public DbMaintenanceScheduler(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/* ---------- FULL BACKUP (hot, DB stays online) ---------- */
	@Scheduled(cron = "${codesync.backup.cron}")
	public void backup() {
		try {
			new File(backupDir).mkdirs();
			String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
			String file = backupDir + "/codesync_" + ts + ".zip";
			jdbc.execute("BACKUP TO '" + file + "'");
			CodeSyncLogger.logInfo("DB backup created: " + file);

			long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
			File[] old = new File(backupDir).listFiles((d, n) -> n.startsWith("codesync_") && n.endsWith(".zip"));
			if (old != null)
				for (File f : old)
					if (f.lastModified() < cutoff)
						f.delete();
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Backup failed", e);
		}
	}

	/* ---------- ARCHIVE OLD ROWS (AUDIT + SHARED FILES) TO A SEPARATE H2 FILE ---------- */
	@Scheduled(cron = "${codesync.archive.cron}")
	public void archiveOldData() {
		new File(archiveDir).mkdirs();
		// one archive file per day (use YearMonth.now() for one per month)
		String url = "jdbc:h2:file:" + archiveDir + "/codesync_archive_" + LocalDate.now() + ";AUTO_SERVER=TRUE";

		try (Connection arch = DriverManager.getConnection(url, dbUser, dbPass)) {
			try (Statement st = arch.createStatement()) {
				st.execute(AUDIT_DDL);
				st.execute("CREATE INDEX IF NOT EXISTS IDX_ARCH_AUDIT_CREATED ON CODE_SYNC_AUDIT_ARCHIVE(CREATED_AT)");
				st.execute(SHARED_FILE_DDL);
				st.execute("CREATE INDEX IF NOT EXISTS IDX_ARCH_SF_UPLOADED ON CODE_SYNC_SHARED_FILE_ARCHIVE(UPLOADED_AT)");
				st.execute("CREATE INDEX IF NOT EXISTS IDX_ARCH_SF_SHARE_KEY ON CODE_SYNC_SHARED_FILE_ARCHIVE(SHARE_KEY)");
			}

			// each table in its own try so a failure in one doesn't block the other
			try {
				Timestamp auditCutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(auditRetentionDays));
				int n = archiveTable(arch, "CODE_SYNC_AUDIT", "CODE_SYNC_AUDIT_ARCHIVE", "CREATED_AT < ?",
						auditCutoff);
				CodeSyncLogger.logInfo("Audit archive done, rows moved: " + n + " -> " + url);
			} catch (Exception e) {
				CodeSyncLogger.logError(getClass(), "Audit archive failed", e);
			}

			try {
				Timestamp sfCutoff = Timestamp.valueOf(LocalDateTime.now().minusDays(sharedFileRetentionDays));
				Timestamp now = new Timestamp(System.currentTimeMillis());
				// ONLY rows the app no longer needs: inactive/deleted or expired, AND old enough
				int n = archiveTable(arch, "CODE_SYNC_SHARED_FILE", "CODE_SYNC_SHARED_FILE_ARCHIVE",
						"UPLOADED_AT < ? AND (IS_ACTIVE = FALSE OR (EXPIRES_AT IS NOT NULL AND EXPIRES_AT < ?))",
						sfCutoff, now);
				CodeSyncLogger.logInfo("Shared file archive done, rows moved: " + n + " -> " + url);
			} catch (Exception e) {
				CodeSyncLogger.logError(getClass(), "Shared file archive failed", e);
			}
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Archive failed", e);
		}
	}

	/**
	 * Copies matching rows to the archive DB in batches, commits the archive, and
	 * only then deletes exactly those IDs from the main DB.
	 */
	private int archiveTable(Connection arch, String table, String archiveTable, String where, Object... params)
			throws Exception {
		int total = 0;
		arch.setAutoCommit(false);

		while (true) {
			List<Map<String, Object>> rows = jdbc.queryForList(
					"SELECT * FROM " + table + " WHERE " + where + " ORDER BY ID FETCH FIRST " + BATCH + " ROWS ONLY",
					params);
			if (rows.isEmpty())
				break;

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
				throw e;
			}

			jdbc.batchUpdate("DELETE FROM " + table + " WHERE ID = ?", ids);
			total += rows.size();
		}
		return total;
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
			  IS_FILE_MOVED BOOLEAN, IS_FILE_DELETED_DIRECTLY BOOLEAN
			)""";
}


I've updated `DbMaintenanceScheduler.java` with stronger logging and a more compressed backup option. I haven't compiled or run it, so test it on a copy of your DB first.

## Stronger logs

- Every line is tagged `[BACKUP]` or `[ARCHIVE]` plus a short run ID, so you can grep one run end to end.
- **Backup** logs the mode, the main DB size, the backup size and its ratio to the main DB, the duration, and every old backup it deletes.
- **Archive** logs, per table, the number of eligible rows, how many were copied, how many were deleted, and how many are still eligible afterwards. It also logs the archive file size and the main DB size before and after.
- It logs a `WARN` if copied and deleted counts differ, or if eligible rows remain after the run. It also logs the first and last ID of any batch that fails.
- The backup is checked after it is made (the file must exist and not be empty). A half-written compressed file is deleted so it can't be mistaken for a good backup.
- A lock stops backup and archive from running at the same time. The cron properties now default to `-`, which tells Spring to disable that job instead of failing at startup when the property is missing.

I don't know which methods your `CodeSyncLogger` has, so I only used `logInfo`, `logDebug` and `logError`. Warnings are `logInfo` lines with a `WARN` prefix. If you have a real `logWarn`, change the `warn()` helper at the bottom of the file.

## More compressed backups

Set `codesync.backup.mode` to one of these:

| Mode | Output | Size | Notes |
|---|---|---|---|
| `zip` (default) | `.zip` of the `.mv.db` file | largest | Fastest, and it restores by simply copying the file. |
| `script-gz` | `.sql.gz` | smaller | Plain SQL, no extra tools needed. |
| `script-xz` | `.sql.xz` | much smaller | Best ratio, slower, needs `xz`. |
| `script-zstd` | `.sql.zst` | close to xz | Much faster than xz, needs `zstd`. |

For your audit data (lots of repetitive request and response bodies), `script-xz` or `script-zstd` should be noticeably smaller than the zip.

```properties
codesync.backup.mode=script-zstd
codesync.backup.compress-timeout-minutes=60
codesync.db.file=/opt/codesync/db/codesync.mv.db
```

Install the tools on Linux with `sudo apt install xz-utils zstd`. On RHEL-based systems, use `dnf install xz zstd`.

## Things to know

1. **Memory:** `xz -9` can use about 700 MB of RAM and `zstd -19 --long=27` about 128 MB. On a small VM, prefer zstd, or lower xz to `-6` in the code.
2. **Restore:** the script modes are slower to restore than a zip, because the SQL is replayed. First decompress with `xz -d file.sql.xz` or `zstd -d file.sql.zst`. Then load it into a new empty DB with `java -cp h2.jar org.h2.tools.RunScript -url jdbc:h2:file:/tmp/restore/codesync -user code_sync -script file.sql`. Use the same H2 jar version your app uses.
3. **Locking:** `SCRIPT` reads every table, so it can hold locks for a short time on a big DB. Keep it at 2 AM. The zip mode (`BACKUP TO`) is the safest for a hot backup.
4. **Existing zips:** re-compressing a zip you already have gains almost nothing, because it is already deflated. Extract it first, for example `unzip -p codesync_X.zip | xz -9 > codesync_X.mv.db.xz`. This works because the zip holds a single file.
5. **Archive files:** the archive `.mv.db` files can be compressed the same way, but only once they're no longer written to. For example, run `xz -9` on the previous days' files. To open one again, decompress it first.


package com.cs.scheduler;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
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

	/** Backup and archive never run at the same time (also protects manual calls). */
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

	@Value("${codesync.db.file:/opt/codesync/db/codesync.mv.db}")
	private String dbFilePath;

	@Value("${codesync.archive.dir}")
	private String archiveDir;

	@Value("${codesync.archive.retention-days:7}")
	private int auditRetentionDays;

	@Value("${codesync.archive.shared-file-retention-days:30}")
	private int sharedFileRetentionDays;

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
	/* BACKUP */
	/* ====================================================================== */

	@Scheduled(cron = "${codesync.backup.cron:-}")
	public void backup() {
		if (!backupEnabled) {
			CodeSyncLogger.logDebug("[BACKUP] skipped: codesync.backup.cron.enabled=false");
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

			long dbSize = fileSize(Path.of(dbFilePath));
			info("BACKUP", runId, "START mode=" + backupMode + " target=" + base + ".* mainDbSize=" + human(dbSize)
					+ " (" + dbFilePath + ")");

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
		long cutoff = System.currentTimeMillis() - keepDays * 86_400_000L;
		File[] files = new File(backupDir).listFiles((d, n) -> n.startsWith("codesync_"));
		int deleted = 0, kept = 0;
		long freed = 0;
		if (files != null) {
			for (File f : files) {
				if (f.lastModified() < cutoff) {
					long s = f.length();
					if (f.delete()) {
						deleted++;
						freed += s;
						info("BACKUP", runId, "RETENTION deleted " + f.getName() + " (" + human(s) + ")");
					} else {
						warn("BACKUP", runId, "RETENTION could not delete " + f.getAbsolutePath());
					}
				} else {
					kept++;
				}
			}
		}
		info("BACKUP", runId, "RETENTION keepDays=" + keepDays + " deleted=" + deleted + " freed=" + human(freed)
				+ " kept=" + kept);
	}

	/* ====================================================================== */
	/* ARCHIVE */
	/* ====================================================================== */

	@Scheduled(cron = "${codesync.archive.cron:-}")
	public void archiveOldData() {
		if (!archiveEnabled) {
			CodeSyncLogger.logDebug("[ARCHIVE] skipped: codesync.archive.cron.enabled=false");
			return;
		}
		if (!lock.tryLock()) {
			CodeSyncLogger.logInfo("[ARCHIVE] WARN skipped: another maintenance job is still running");
			return;
		}

		String runId = newRunId();
		long t0 = System.currentTimeMillis();
		Path archiveFile = Path.of(archiveDir, "codesync_archive_" + LocalDate.now() + ".mv.db");
		// one archive file per day (use YearMonth.now() for one per month)
		String url = "jdbc:h2:file:" + archiveDir + "/codesync_archive_" + LocalDate.now() + ";AUTO_SERVER=TRUE";

		Result audit = null, shared = null;
		try {
			Files.createDirectories(Path.of(archiveDir));
			info("ARCHIVE", runId, "START auditRetentionDays=" + auditRetentionDays + " sharedFileRetentionDays="
					+ sharedFileRetentionDays + " mainDbSize=" + human(fileSize(Path.of(dbFilePath))) + " target="
					+ url);

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
							+ human(fileSize(archiveFile)) + " mainDbSizeNow=" + human(fileSize(Path.of(dbFilePath)))
							+ " totalDurationMs=" + (System.currentTimeMillis() - t0));
			info("ARCHIVE", runId,
					"NOTE main DB file does not shrink by itself; freed space is reused. Run SHUTDOWN COMPACT in a maintenance window to shrink it.");
		} catch (Exception e) {
			error("ARCHIVE", runId, "FAILED after " + (System.currentTimeMillis() - t0) + "ms", e);
		} finally {
			lock.unlock();
		}
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
			CodeSyncLogger.logDebug("[ARCHIVE][" + runId + "] " + table + " batch #" + batchNo + " copied="
					+ rows.size() + " deleted=" + batchDeleted + " totalCopied=" + copied);
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

	/* ====================================================================== */
	/* HELPERS */
	/* ====================================================================== */

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
			  IS_FILE_MOVED BOOLEAN, IS_FILE_DELETED_DIRECTLY BOOLEAN
			)""";
}