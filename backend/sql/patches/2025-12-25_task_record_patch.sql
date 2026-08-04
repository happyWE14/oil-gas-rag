-- Idempotent patch to align an existing Postgres DB with current code/schema expectations.
-- Safe to run multiple times.

-- Some environments may have recreated tables from stale DDL and missed the `id` column
-- (MyBatis-Plus expects it via @TableId). Backfill safely if needed.
ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS id BIGINT;

-- Align missing retry/scheduling columns (safe for stale DDL that predates auto-retry support).
ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 3;

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

DO $$
DECLARE
    seq_name TEXT;
BEGIN
    -- Ensure `id` has a working sequence default.
    SELECT pg_get_serial_sequence('task_record', 'id') INTO seq_name;
    IF seq_name IS NULL THEN
        seq_name := 'task_record_id_seq';
        EXECUTE format('CREATE SEQUENCE IF NOT EXISTS %I', seq_name);
        EXECUTE format('ALTER TABLE task_record ALTER COLUMN id SET DEFAULT nextval(%L)', seq_name);
        EXECUTE format('ALTER SEQUENCE %I OWNED BY task_record.id', seq_name);
    END IF;

    -- Backfill NULL ids (in case the column existed but was nullable).
    EXECUTE format('UPDATE task_record SET id = nextval(%L) WHERE id IS NULL', seq_name);

    -- Ensure the sequence won't generate duplicates after backfilling / manual inserts.
    EXECUTE format('SELECT setval(%L, GREATEST((SELECT COALESCE(MAX(id), 0) FROM task_record), 1), true)', seq_name);

    -- Add a PK if none exists. If a different PK already exists, keep it (non-destructive).
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'task_record'::regclass
          AND contype = 'p'
    ) THEN
        EXECUTE 'ALTER TABLE task_record ADD CONSTRAINT task_record_pkey PRIMARY KEY (id)';
    END IF;

    -- `id` should be NOT NULL for mapper compatibility.
    EXECUTE 'ALTER TABLE task_record ALTER COLUMN id SET NOT NULL';
END $$;

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS error_category VARCHAR(20);

ALTER TABLE task_record
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_task_error_category
    ON task_record (error_category)
    WHERE error_category IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_task_next_retry_at
    ON task_record (next_retry_at);

-- Strong-consistency guard: at most one active task per (paper_id, task_type).
-- Also treats due transient FAILED tasks as "active" for dedup to avoid duplicate scheduling storms.
CREATE UNIQUE INDEX IF NOT EXISTS ux_task_active_per_paper_type
    ON task_record (paper_id, task_type)
    WHERE paper_id IS NOT NULL
      AND (
        status IN ('WAITING', 'RUNNING')
        OR (status = 'FAILED' AND error_category = 'TRANSIENT' AND retry_count < max_retries)
      );
