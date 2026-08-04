-- Add error_reason column to task_record for fine-grained failure classification
-- This column stores FailureReason enum values (RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR, etc.)
-- while error_category remains for backward compatibility (TRANSIENT/PERMANENT)

ALTER TABLE task_record ADD COLUMN IF NOT EXISTS error_reason VARCHAR(32) NULL;

COMMENT ON COLUMN task_record.error_reason IS 'Fine-grained failure reason: RATE_LIMITED, SERVER_ERROR, NETWORK_ERROR, CONCURRENCY_CONFLICT, AUTH_FAILURE, NOT_FOUND, INVALID_REQUEST, CONFIGURATION_ERROR, UNKNOWN';

CREATE INDEX IF NOT EXISTS idx_task_error_reason ON task_record (error_reason) WHERE error_reason IS NOT NULL;
