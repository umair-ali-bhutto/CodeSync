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
