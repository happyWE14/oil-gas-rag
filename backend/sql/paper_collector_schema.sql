-- PostgreSQL schema for paper-collector (uses pgvector)
CREATE EXTENSION IF NOT EXISTS vector;

-- ========== 基础/独立表 ==========
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
CREATE INDEX IF NOT EXISTS idx_event_outbox_status_retry ON event_outbox (status, next_retry_at);
CREATE INDEX IF NOT EXISTS idx_event_outbox_aggregate ON event_outbox (aggregate_id);

CREATE TABLE IF NOT EXISTS file_detail (
    id                  VARCHAR(32)  PRIMARY KEY,
    url                 VARCHAR(512) NOT NULL,
    size                BIGINT       NULL,
    filename            VARCHAR(256) NULL,
    original_filename   VARCHAR(256) NULL,
    base_path           VARCHAR(256) NULL,
    path                VARCHAR(256) NULL,
    ext                 VARCHAR(32)  NULL,
    content_type        VARCHAR(128) NULL,
    platform            VARCHAR(32)  NULL,
    th_url              VARCHAR(512) NULL,
    th_filename         VARCHAR(256) NULL,
    th_size             BIGINT       NULL,
    th_content_type     VARCHAR(128) NULL,
    object_id           VARCHAR(32)  NULL,
    object_type         VARCHAR(32)  NULL,
    metadata            TEXT         NULL,
    user_metadata       TEXT         NULL,
    th_metadata         TEXT         NULL,
    th_user_metadata    TEXT         NULL,
    attr                TEXT         NULL,
    file_acl            VARCHAR(32)  NULL,
    th_file_acl         VARCHAR(32)  NULL,
    hash_info           TEXT         NULL,
    upload_id           VARCHAR(128) NULL,
    upload_status       INTEGER      NULL,
    create_time         TIMESTAMP    NULL
);
COMMENT ON TABLE file_detail IS '文件记录表';
COMMENT ON COLUMN file_detail.id IS '文件id';
COMMENT ON COLUMN file_detail.url IS '文件访问地址';
COMMENT ON COLUMN file_detail.size IS '文件大小，单位字节';
COMMENT ON COLUMN file_detail.filename IS '文件名称';
COMMENT ON COLUMN file_detail.original_filename IS '原始文件名';
COMMENT ON COLUMN file_detail.base_path IS '基础存储路径';
COMMENT ON COLUMN file_detail.path IS '存储路径';
COMMENT ON COLUMN file_detail.ext IS '文件扩展名';
COMMENT ON COLUMN file_detail.content_type IS 'MIME类型';
COMMENT ON COLUMN file_detail.platform IS '存储平台';
COMMENT ON COLUMN file_detail.th_url IS '缩略图访问路径';
COMMENT ON COLUMN file_detail.th_filename IS '缩略图名称';
COMMENT ON COLUMN file_detail.th_size IS '缩略图大小，单位字节';
COMMENT ON COLUMN file_detail.th_content_type IS '缩略图MIME类型';
COMMENT ON COLUMN file_detail.object_id IS '文件所属对象id';
COMMENT ON COLUMN file_detail.object_type IS '文件所属对象类型';
COMMENT ON COLUMN file_detail.metadata IS '文件元数据';
COMMENT ON COLUMN file_detail.user_metadata IS '文件用户元数据';
COMMENT ON COLUMN file_detail.th_metadata IS '缩略图元数据';
COMMENT ON COLUMN file_detail.th_user_metadata IS '缩略图用户元数据';
COMMENT ON COLUMN file_detail.attr IS '附加属性';
COMMENT ON COLUMN file_detail.file_acl IS '文件ACL';
COMMENT ON COLUMN file_detail.th_file_acl IS '缩略图文件ACL';
COMMENT ON COLUMN file_detail.hash_info IS '哈希信息';
COMMENT ON COLUMN file_detail.upload_id IS '上传ID，仅在手动分片上传时使用';
COMMENT ON COLUMN file_detail.upload_status IS '上传状态，1：初始化完成，2：上传完成';
COMMENT ON COLUMN file_detail.create_time IS '创建时间';

CREATE TABLE IF NOT EXISTS file_part_detail (
    id              VARCHAR(32) PRIMARY KEY,
    platform        VARCHAR(32)  NULL,
    upload_id       VARCHAR(128) NULL,
    e_tag           VARCHAR(255) NULL,
    part_number     INTEGER      NULL,
    part_size       BIGINT       NULL,
    hash_info       TEXT         NULL,
    create_time     TIMESTAMP    NULL
);
COMMENT ON TABLE file_part_detail IS '文件分片信息表';
COMMENT ON COLUMN file_part_detail.id IS '分片id';
COMMENT ON COLUMN file_part_detail.platform IS '存储平台';
COMMENT ON COLUMN file_part_detail.upload_id IS '上传ID，仅在手动分片上传时使用';
COMMENT ON COLUMN file_part_detail.e_tag IS '分片 ETag';
COMMENT ON COLUMN file_part_detail.part_number IS '分片号';
COMMENT ON COLUMN file_part_detail.part_size IS '文件大小，单位字节';
COMMENT ON COLUMN file_part_detail.hash_info IS '哈希信息';
COMMENT ON COLUMN file_part_detail.create_time IS '创建时间';

-- ========== 核心表 ==========
CREATE TABLE IF NOT EXISTS paper (
    paper_id          VARCHAR(64)  PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    authors           VARCHAR(2000) NOT NULL,
    doi               VARCHAR(255) NULL,
    year_published    INTEGER      NULL,
    paper_source      VARCHAR(50)  NOT NULL,
    abstract_content  TEXT         NULL,
    download_url      VARCHAR(1024) NULL,
    citation_count    INTEGER      NULL,
    reference_count   INTEGER      NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'DISCOVERED',
    version           BIGINT       NOT NULL DEFAULT 0,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    create_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE IF EXISTS paper ADD COLUMN IF NOT EXISTS reference_count INTEGER NULL;
COMMENT ON TABLE paper IS '论文表（聚合根）';
COMMENT ON COLUMN paper.paper_id IS '论文唯一ID';
COMMENT ON COLUMN paper.title IS '标题';
COMMENT ON COLUMN paper.authors IS '作者列表';
COMMENT ON COLUMN paper.doi IS 'DOI';
COMMENT ON COLUMN paper.year_published IS '发表年份';
COMMENT ON COLUMN paper.paper_source IS '来源平台';
COMMENT ON COLUMN paper.abstract_content IS '摘要';
COMMENT ON COLUMN paper.download_url IS '下载URL';
COMMENT ON COLUMN paper.citation_count IS '引用数';
COMMENT ON COLUMN paper.reference_count IS '参考文献数';
COMMENT ON COLUMN paper.status IS '状态';
COMMENT ON COLUMN paper.version IS '乐观锁版本';
COMMENT ON COLUMN paper.deleted IS '逻辑删除标识';
COMMENT ON COLUMN paper.create_time IS '创建时间';
COMMENT ON COLUMN paper.update_time IS '更新时间';
CREATE INDEX IF NOT EXISTS idx_paper_status ON paper (status);
CREATE INDEX IF NOT EXISTS idx_paper_source ON paper (paper_source);
-- Cross-source dedup by DOI (case-insensitive) for active (not deleted) rows.
-- Note: uses a partial expression index; see also patches/2026-01-05_paper_doi_dedup.sql for safe rollout on existing DBs.
CREATE UNIQUE INDEX IF NOT EXISTS uq_paper_doi_lower_active
ON paper ((lower(doi)))
WHERE doi IS NOT NULL
  AND deleted = FALSE;

CREATE TABLE IF NOT EXISTS paper_citation (
    id               BIGSERIAL PRIMARY KEY,
    citing_paper_id  VARCHAR(64) NOT NULL,
    cited_paper_id   VARCHAR(64) NOT NULL,
    create_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_citation_citing FOREIGN KEY (citing_paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_citation_cited  FOREIGN KEY (cited_paper_id)  REFERENCES paper (paper_id) ON DELETE CASCADE,
    CONSTRAINT ck_paper_citation_not_self CHECK (citing_paper_id <> cited_paper_id)
);
COMMENT ON TABLE paper_citation IS '论文引用关系（citing -> cited），用于同时表达 citations 和 references';
COMMENT ON COLUMN paper_citation.citing_paper_id IS '引用方论文ID（citing paper）';
COMMENT ON COLUMN paper_citation.cited_paper_id IS '被引用论文ID（cited paper）';
COMMENT ON COLUMN paper_citation.create_time IS '创建时间';
COMMENT ON COLUMN paper_citation.update_time IS '更新时间';
CREATE UNIQUE INDEX IF NOT EXISTS uq_paper_citation_citing_cited ON paper_citation (citing_paper_id, cited_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_citation_citing ON paper_citation (citing_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_citation_cited ON paper_citation (cited_paper_id);

CREATE TABLE IF NOT EXISTS paper_download (
    id                BIGSERIAL PRIMARY KEY,
    paper_id          VARCHAR(64)   NOT NULL,
    source_url        VARCHAR(1024) NULL,
    oss_url           VARCHAR(1024) NULL,
    file_size         BIGINT        NULL,
    try_times         INTEGER       NULL DEFAULT 0,
    download_status   VARCHAR(32)   NOT NULL,
    error_message     VARCHAR(500)  NULL,
    start_time        TIMESTAMP     NULL,
    finish_time       TIMESTAMP     NULL,
    CONSTRAINT fk_download_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE paper_download IS '论文下载信息';
COMMENT ON COLUMN paper_download.paper_id IS '论文ID';
COMMENT ON COLUMN paper_download.source_url IS '源链接';
COMMENT ON COLUMN paper_download.oss_url IS 'OSS 链接';
COMMENT ON COLUMN paper_download.file_size IS '文件大小';
COMMENT ON COLUMN paper_download.try_times IS '尝试次数';
COMMENT ON COLUMN paper_download.download_status IS 'PENDING/IN_PROGRESS/COMPLETED/FAILED';
COMMENT ON COLUMN paper_download.error_message IS '错误信息';
COMMENT ON COLUMN paper_download.start_time IS '开始时间';
COMMENT ON COLUMN paper_download.finish_time IS '结束时间';
CREATE INDEX IF NOT EXISTS idx_dl_paper ON paper_download (paper_id);

CREATE TABLE IF NOT EXISTS paper_transform (
    id                   BIGSERIAL PRIMARY KEY,
    paper_id             VARCHAR(64)   NOT NULL,
    transform_provider   VARCHAR(50)   NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    markdown_path        VARCHAR(1024) NULL,
    error_message        TEXT          NULL,
    start_time           TIMESTAMP     NULL,
    finish_time          TIMESTAMP     NULL,
    CONSTRAINT fk_transform_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE paper_transform IS 'PDF 转换信息';
COMMENT ON COLUMN paper_transform.paper_id IS '论文ID';
COMMENT ON COLUMN paper_transform.transform_provider IS '转换提供方';
COMMENT ON COLUMN paper_transform.status IS 'PENDING/PROCESSING/COMPLETED/FAILED';
COMMENT ON COLUMN paper_transform.markdown_path IS 'Markdown 存储路径';
COMMENT ON COLUMN paper_transform.error_message IS '错误信息';
COMMENT ON COLUMN paper_transform.start_time IS '开始时间';
COMMENT ON COLUMN paper_transform.finish_time IS '结束时间';
CREATE INDEX IF NOT EXISTS idx_transform_paper ON paper_transform (paper_id);

CREATE TABLE IF NOT EXISTS paper_content (
    id           BIGSERIAL PRIMARY KEY,
    paper_id     VARCHAR(64)  NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,
    source_type  VARCHAR(32)  NOT NULL DEFAULT 'OCR',
    charset      VARCHAR(64)  NULL,
    checksum     VARCHAR(128) NULL,
    size         BIGINT       NULL,
    content      TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_content_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE paper_content IS '论文正文内容';
COMMENT ON COLUMN paper_content.paper_id IS '论文ID';
COMMENT ON COLUMN paper_content.version IS '版本号';
COMMENT ON COLUMN paper_content.source_type IS 'OCR/PDF_EXTRACT/OTHER';
COMMENT ON COLUMN paper_content.charset IS '字符集';
COMMENT ON COLUMN paper_content.checksum IS '内容校验（md5）';
COMMENT ON COLUMN paper_content.size IS '字节大小';
COMMENT ON COLUMN paper_content.content IS '正文内容';
COMMENT ON COLUMN paper_content.created_at IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_content_paper ON paper_content (paper_id, version DESC);

CREATE TABLE IF NOT EXISTS paper_relevant (
    id                              BIGSERIAL PRIMARY KEY,
    paper_id                        VARCHAR(64)  NOT NULL,
    is_relevant                     BOOLEAN      NOT NULL DEFAULT FALSE,
    score                           NUMERIC(5,2) NULL,
    analyzer                        VARCHAR(100) NULL,
    reason                          TEXT         NULL,
    raw_result                      JSONB        NULL,
    keywords                        JSONB        NULL,
    threshold_used                  NUMERIC(5,2) NULL,
    has_extractable_material_info   BOOLEAN      NULL,
    CONSTRAINT fk_relevant_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE paper_relevant IS '相关性分析结果';
COMMENT ON COLUMN paper_relevant.paper_id IS '论文ID';
COMMENT ON COLUMN paper_relevant.is_relevant IS '是否相关';
COMMENT ON COLUMN paper_relevant.score IS '相关性分数';
COMMENT ON COLUMN paper_relevant.analyzer IS '分析器';
COMMENT ON COLUMN paper_relevant.reason IS '原因';
COMMENT ON COLUMN paper_relevant.raw_result IS '模型原始JSON结果';
COMMENT ON COLUMN paper_relevant.keywords IS '关键词快照';
COMMENT ON COLUMN paper_relevant.threshold_used IS '判定阈值';
COMMENT ON COLUMN paper_relevant.has_extractable_material_info IS '可提取材料信息';
CREATE INDEX IF NOT EXISTS idx_relevant_paper ON paper_relevant (paper_id);

CREATE TABLE IF NOT EXISTS model_output_log (
    id            BIGSERIAL PRIMARY KEY,
    paper_id      VARCHAR(64)  NOT NULL,
    task_type     VARCHAR(64)  NOT NULL,
    model_name    VARCHAR(100) NULL,
    model_output  TEXT         NOT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_model_output_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE model_output_log IS '模型输出日志';
COMMENT ON COLUMN model_output_log.paper_id IS '关联论文ID';
COMMENT ON COLUMN model_output_log.task_type IS '任务类型';
COMMENT ON COLUMN model_output_log.model_name IS '模型名称';
COMMENT ON COLUMN model_output_log.model_output IS '模型输出';
COMMENT ON COLUMN model_output_log.create_time IS '创建时间';
CREATE INDEX IF NOT EXISTS idx_model_output_paper_task ON model_output_log (paper_id, task_type);

CREATE TABLE IF NOT EXISTS document_chunk (
    id               BIGSERIAL PRIMARY KEY,
    paper_id         VARCHAR(64)  NOT NULL,
    order_no         INTEGER      NOT NULL,
    content          TEXT         NOT NULL,
    embedding_model  VARCHAR(100) NULL,
    vector           VECTOR(2048) NULL,
    CONSTRAINT fk_chunk_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
COMMENT ON TABLE document_chunk IS '文档分块';
COMMENT ON COLUMN document_chunk.paper_id IS '论文ID';
COMMENT ON COLUMN document_chunk.order_no IS '分块序号';
COMMENT ON COLUMN document_chunk.content IS '分块内容';
COMMENT ON COLUMN document_chunk.embedding_model IS '向量模型';
COMMENT ON COLUMN document_chunk.vector IS '向量值(pgvector)';
CREATE INDEX IF NOT EXISTS idx_chunk_paper ON document_chunk (paper_id, order_no);
-- 用于保证 chunk 的稳定主键(id)不因重复写入而变化：以 (paper_id, order_no) 作为逻辑唯一键进行 upsert。
CREATE UNIQUE INDEX IF NOT EXISTS uq_chunk_paper_order_no ON document_chunk (paper_id, order_no);
-- 当前检索先按 paper_id 限定候选块，再做精确余弦排序；因此不创建全库 ANN 索引。

CREATE TABLE IF NOT EXISTS material_extraction (
    id           BIGSERIAL PRIMARY KEY,
    paper_id     VARCHAR(64)  NOT NULL,
    material     VARCHAR(255) NOT NULL,
    property     VARCHAR(255) NULL,
    method       VARCHAR(255) NULL,
    confidence   NUMERIC(5,3) NULL,
    detail_json  JSONB        NULL,
    CONSTRAINT fk_material_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_material_paper ON material_extraction (paper_id);
COMMENT ON TABLE material_extraction IS '材料提取结果';
COMMENT ON COLUMN material_extraction.paper_id IS '论文ID';
COMMENT ON COLUMN material_extraction.material IS '材料名称';
COMMENT ON COLUMN material_extraction.property IS '材料性质';
COMMENT ON COLUMN material_extraction.method IS '提取方法';
COMMENT ON COLUMN material_extraction.confidence IS '置信度';
COMMENT ON COLUMN material_extraction.detail_json IS '提取结果JSON';

CREATE TABLE IF NOT EXISTS paper_state_event (
    id           BIGSERIAL PRIMARY KEY,
    paper_id     VARCHAR(64)  NOT NULL,
    from_state   VARCHAR(50)  NOT NULL,
    to_state     VARCHAR(50)  NOT NULL,
    operator     VARCHAR(100) NULL,
    reason       VARCHAR(500) NULL,
    occurred_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_state_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_state_paper ON paper_state_event (paper_id);
COMMENT ON TABLE paper_state_event IS '状态变更流水';
COMMENT ON COLUMN paper_state_event.paper_id IS '论文ID';
COMMENT ON COLUMN paper_state_event.from_state IS '原状态';
COMMENT ON COLUMN paper_state_event.to_state IS '目标状态';
COMMENT ON COLUMN paper_state_event.operator IS '操作者';
COMMENT ON COLUMN paper_state_event.reason IS '原因';
COMMENT ON COLUMN paper_state_event.occurred_at IS '发生时间';

CREATE TABLE IF NOT EXISTS paper_search_config (
    id                   BIGSERIAL PRIMARY KEY,
    platform             VARCHAR(64)   NOT NULL,
    query                VARCHAR(1024) NOT NULL,
    current_page         INTEGER       NOT NULL DEFAULT 1,
    page_size            INTEGER       NOT NULL DEFAULT 20,
    total_page           INTEGER       NULL,
    total_count          INTEGER       NULL,
    search_fields        VARCHAR(500)  NULL,
    sort_order           VARCHAR(100)  NULL,
    filters              TEXT          NULL,
    search_status        VARCHAR(32)   NULL DEFAULT 'ACTIVE',
    last_run_time        TIMESTAMP     NULL,
    next_run_time        TIMESTAMP     NULL,
    cron_expression      VARCHAR(100)  NULL,
    max_empty_pages      INTEGER       NULL DEFAULT 3,
    empty_page_count     INTEGER       NULL DEFAULT 0,
    last_failure_reason  VARCHAR(500)  NULL,
    query_snapshot       JSONB         NULL,
    keywords_snapshot    JSONB         NULL,
    template_name        VARCHAR(100)  NULL,
    batch_id             VARCHAR(64)   NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_platform ON paper_search_config (platform);
CREATE INDEX IF NOT EXISTS idx_status ON paper_search_config (search_status);
CREATE INDEX IF NOT EXISTS idx_next_run_time ON paper_search_config (next_run_time);
COMMENT ON TABLE paper_search_config IS '论文搜索配置表';
COMMENT ON COLUMN paper_search_config.platform IS '搜索平台';
COMMENT ON COLUMN paper_search_config.query IS '搜索关键词';
COMMENT ON COLUMN paper_search_config.current_page IS '当前页码';
COMMENT ON COLUMN paper_search_config.page_size IS '每页大小';
COMMENT ON COLUMN paper_search_config.total_page IS '总页数';
COMMENT ON COLUMN paper_search_config.total_count IS '总记录数';
COMMENT ON COLUMN paper_search_config.search_fields IS '搜索字段(JSON)';
COMMENT ON COLUMN paper_search_config.sort_order IS '排序方式';
COMMENT ON COLUMN paper_search_config.filters IS '过滤条件';
COMMENT ON COLUMN paper_search_config.search_status IS '状态';
COMMENT ON COLUMN paper_search_config.last_run_time IS '最后执行时间';
COMMENT ON COLUMN paper_search_config.next_run_time IS '下次执行时间';
COMMENT ON COLUMN paper_search_config.cron_expression IS 'Cron 表达式';
COMMENT ON COLUMN paper_search_config.max_empty_pages IS '连续空页阈值';
COMMENT ON COLUMN paper_search_config.empty_page_count IS '当前连续空页次数';
COMMENT ON COLUMN paper_search_config.last_failure_reason IS '最近一次失败原因';
COMMENT ON COLUMN paper_search_config.query_snapshot IS '查询快照';
COMMENT ON COLUMN paper_search_config.keywords_snapshot IS '关键词簇快照';
COMMENT ON COLUMN paper_search_config.template_name IS '使用的查询模板';
COMMENT ON COLUMN paper_search_config.batch_id IS '查询批次ID';
COMMENT ON COLUMN paper_search_config.create_time IS '创建时间';
COMMENT ON COLUMN paper_search_config.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS task_record (
    id             BIGSERIAL PRIMARY KEY,
    paper_id       VARCHAR(64)  NULL,
    task_type      VARCHAR(64)  NOT NULL,
    task_name      VARCHAR(255) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    description    TEXT         NULL,
    start_time     TIMESTAMP    NULL,
    end_time       TIMESTAMP    NULL,
    duration_ms    BIGINT       NULL,
    error_message  TEXT         NULL,
    error_category VARCHAR(20)  NULL,
    error_reason   VARCHAR(32)  NULL,
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    max_retries    INTEGER      NOT NULL DEFAULT 3,
    next_retry_at  TIMESTAMP    NULL,
    parameters     TEXT         NULL,
    result         TEXT         NULL,
    created_by     VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_task_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_task_paper ON task_record (paper_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON task_record (status);
CREATE INDEX IF NOT EXISTS idx_task_type ON task_record (task_type);
CREATE INDEX IF NOT EXISTS idx_task_error_category ON task_record (error_category) WHERE error_category IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_error_reason ON task_record (error_reason) WHERE error_reason IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_next_retry_at ON task_record (next_retry_at);
CREATE UNIQUE INDEX IF NOT EXISTS ux_task_active_per_paper_type
ON task_record (paper_id, task_type)
WHERE paper_id IS NOT NULL
  AND (
    status IN ('WAITING', 'RUNNING')
    OR (status = 'FAILED' AND error_category = 'TRANSIENT' AND retry_count < max_retries)
  );
COMMENT ON TABLE task_record IS '任务记录';
COMMENT ON COLUMN task_record.paper_id IS '关联论文ID';
COMMENT ON COLUMN task_record.task_type IS '任务类型';
COMMENT ON COLUMN task_record.task_name IS '任务名称';
COMMENT ON COLUMN task_record.status IS '任务状态';
COMMENT ON COLUMN task_record.description IS '描述';
COMMENT ON COLUMN task_record.start_time IS '开始时间';
COMMENT ON COLUMN task_record.end_time IS '结束时间';
COMMENT ON COLUMN task_record.duration_ms IS '耗时ms';
COMMENT ON COLUMN task_record.error_message IS '错误信息';
COMMENT ON COLUMN task_record.error_category IS '错误类别：TRANSIENT(可重试)/PERMANENT(不可重试)';
COMMENT ON COLUMN task_record.error_reason IS '细分错误原因：RATE_LIMITED/SERVER_ERROR/NETWORK_ERROR/CONCURRENCY_CONFLICT/AUTH_FAILURE/NOT_FOUND/INVALID_REQUEST/CONFIGURATION_ERROR/UNKNOWN';
COMMENT ON COLUMN task_record.retry_count IS '重试次数';
COMMENT ON COLUMN task_record.max_retries IS '最大重试次数';
COMMENT ON COLUMN task_record.next_retry_at IS '下次自动重试时间（可空）';
COMMENT ON COLUMN task_record.parameters IS '参数';
COMMENT ON COLUMN task_record.result IS '结果';
COMMENT ON COLUMN task_record.created_by IS '创建人';
COMMENT ON COLUMN task_record.create_time IS '创建时间';
COMMENT ON COLUMN task_record.update_time IS '更新时间';

CREATE TABLE IF NOT EXISTS system_config (
    id            BIGSERIAL PRIMARY KEY,
    config_key    VARCHAR(255) NOT NULL,
    config_value  TEXT         NULL,
    config_type   VARCHAR(50)  NOT NULL DEFAULT 'STRING',
    description   VARCHAR(500) NULL,
    category      VARCHAR(100) NULL,
    is_encrypted  BOOLEAN      NULL DEFAULT FALSE,
    is_readonly   BOOLEAN      NULL DEFAULT FALSE,
    created_by    VARCHAR(100) NULL,
    updated_by    VARCHAR(100) NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_config_key UNIQUE (config_key)
);
COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.config_key IS '配置键';
COMMENT ON COLUMN system_config.config_value IS '配置值';
COMMENT ON COLUMN system_config.config_type IS '配置类型';
COMMENT ON COLUMN system_config.description IS '描述';
COMMENT ON COLUMN system_config.category IS '分类';
COMMENT ON COLUMN system_config.is_encrypted IS '是否加密';
COMMENT ON COLUMN system_config.is_readonly IS '是否只读';
COMMENT ON COLUMN system_config.created_by IS '创建人';
COMMENT ON COLUMN system_config.updated_by IS '更新人';
COMMENT ON COLUMN system_config.create_time IS '创建时间';
COMMENT ON COLUMN system_config.update_time IS '更新时间';

-- ========== Full-Text Search (FTS) Columns + Indexes ==========
ALTER TABLE paper ADD COLUMN IF NOT EXISTS paper_fts tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(abstract_content, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(authors, '')), 'C')
    ) STORED;
CREATE INDEX IF NOT EXISTS idx_paper_fts ON paper USING GIN(paper_fts);
COMMENT ON COLUMN paper.paper_fts IS 'FTS向量: A=title, B=abstract, C=authors';

ALTER TABLE material_extraction ADD COLUMN IF NOT EXISTS material_fts tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(material, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(property, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(method, '')), 'C')
    ) STORED;
CREATE INDEX IF NOT EXISTS idx_material_fts ON material_extraction USING GIN(material_fts);
COMMENT ON COLUMN material_extraction.material_fts IS 'FTS向量: A=material, B=property, C=method';

ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS chunk_fts tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;
CREATE INDEX IF NOT EXISTS idx_chunk_fts ON document_chunk USING GIN(chunk_fts);
COMMENT ON COLUMN document_chunk.chunk_fts IS 'FTS向量';

-- ========== Material Metric Table (Structured Metrics) ==========
CREATE TABLE IF NOT EXISTS material_metric (
    id              BIGSERIAL PRIMARY KEY,
    paper_id        VARCHAR(64)  NOT NULL,
    material_key    VARCHAR(255) NOT NULL,
    metric_key      VARCHAR(64)  NOT NULL,
    value_type      VARCHAR(16)  NOT NULL CHECK (value_type IN ('number', 'range', 'text')),
    value_num       DOUBLE PRECISION NULL,
    value_min       DOUBLE PRECISION NULL,
    value_max       DOUBLE PRECISION NULL,
    value_text      TEXT NULL,
    unit            VARCHAR(32)  NULL,
    qualifier       VARCHAR(16)  NULL,
    conditions      JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence      NUMERIC(4,3) NULL,
    chunk_ids       TEXT[] NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_metric_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_material_metric_paper ON material_metric (paper_id);
CREATE INDEX IF NOT EXISTS idx_material_metric_material ON material_metric (material_key);
CREATE INDEX IF NOT EXISTS idx_material_metric_metric ON material_metric (metric_key);
CREATE INDEX IF NOT EXISTS idx_material_metric_value_num ON material_metric (value_num) WHERE value_type = 'number';
CREATE INDEX IF NOT EXISTS idx_material_metric_metric_value ON material_metric (metric_key, value_num);
CREATE INDEX IF NOT EXISTS idx_material_metric_conditions ON material_metric USING GIN (conditions jsonb_path_ops);
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_metric_logical_key 
    ON material_metric (paper_id, material_key, metric_key, md5(conditions::text), coalesce(value_num::text, value_text, ''));
COMMENT ON TABLE material_metric IS '材料指标表 (结构化指标，从 detail_json 展开)';
COMMENT ON COLUMN material_metric.material_key IS '材料标识 (normalized: lower, trim)';
COMMENT ON COLUMN material_metric.metric_key IS '指标类型 (e.g. apparent_viscosity_mPa_s)';
COMMENT ON COLUMN material_metric.value_type IS '值类型: number/range/text';
COMMENT ON COLUMN material_metric.conditions IS '测量条件 (JSONB): temperature_C, shear_rate_s_1, etc.';
COMMENT ON COLUMN material_metric.chunk_ids IS '来源chunk ID列表 (用于引用溯源)';

-- ========== Material Observation Table (Unstructured Insights) ==========
CREATE TABLE IF NOT EXISTS material_observation (
    id              BIGSERIAL PRIMARY KEY,
    paper_id        VARCHAR(64)  NOT NULL,
    material_key    VARCHAR(255) NOT NULL,
    type            VARCHAR(32)  NOT NULL,
    content         TEXT NOT NULL,
    content_fts     tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    confidence      NUMERIC(4,3) NULL,
    chunk_ids       TEXT[] NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_observation_paper FOREIGN KEY (paper_id) REFERENCES paper (paper_id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_material_observation_paper ON material_observation (paper_id);
CREATE INDEX IF NOT EXISTS idx_material_observation_material ON material_observation (material_key);
CREATE INDEX IF NOT EXISTS idx_material_observation_type ON material_observation (type);
CREATE INDEX IF NOT EXISTS idx_material_observation_content_fts ON material_observation USING GIN (content_fts);
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_observation_logical_key 
    ON material_observation (paper_id, material_key, type, md5(content));
COMMENT ON TABLE material_observation IS '材料观察表 (难结构化的文本信息)';
COMMENT ON COLUMN material_observation.type IS '观察类型: formulation, result_summary, measurement_method, comparison, limitation';
COMMENT ON COLUMN material_observation.content_fts IS 'FTS向量';
