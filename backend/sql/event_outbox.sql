-- PostgreSQL event_outbox DDL (pgvector extension assumed created separately if full schema not executed)
CREATE TABLE IF NOT EXISTS event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(255) NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    max_retries     INTEGER      NOT NULL DEFAULT 3,
    sent_at         TIMESTAMP    NULL,
    next_retry_at   TIMESTAMP    NULL,
    error_message   TEXT         NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_event_outbox_status_retry ON event_outbox (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_event_outbox_aggregate ON event_outbox (aggregate_id);

COMMENT ON TABLE event_outbox IS '事务性发件箱';
COMMENT ON COLUMN event_outbox.event_id IS '事件唯一ID';
COMMENT ON COLUMN event_outbox.event_type IS '事件类型';
COMMENT ON COLUMN event_outbox.aggregate_id IS '聚合根ID';
COMMENT ON COLUMN event_outbox.payload IS '事件内容';
COMMENT ON COLUMN event_outbox.status IS 'PENDING/SENT/FAILED';
COMMENT ON COLUMN event_outbox.retry_count IS '重试次数';
COMMENT ON COLUMN event_outbox.max_retries IS '最大重试次数';
COMMENT ON COLUMN event_outbox.sent_at IS '发送时间';
COMMENT ON COLUMN event_outbox.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN event_outbox.error_message IS '错误信息';
COMMENT ON COLUMN event_outbox.created_at IS '创建时间';
COMMENT ON COLUMN event_outbox.updated_at IS '更新时间';
