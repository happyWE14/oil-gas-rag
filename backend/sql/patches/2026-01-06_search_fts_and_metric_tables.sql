-- ============================================================================
-- Patch: Search FTS and Metric Tables
-- Date: 2026-01-06
-- Purpose: Enable keyword search (FTS) and structured metric queries
-- 
-- SAFE TO RE-RUN: Uses IF NOT EXISTS throughout.
-- DOES NOT MODIFY existing table structures (only adds columns/tables/indexes).
-- ============================================================================

-- ============================================================================
-- PART 1: Full-Text Search (FTS) Columns + Indexes
-- ============================================================================

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


-- ============================================================================
-- PART 2: Material Metric Table (Structured Metrics)
-- ============================================================================

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


-- ============================================================================
-- PART 3: Material Observation Table (Unstructured Insights)
-- ============================================================================

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


-- ============================================================================
-- PART 4: Document Chunk FTS
-- ============================================================================

ALTER TABLE document_chunk ADD COLUMN IF NOT EXISTS chunk_fts tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX IF NOT EXISTS idx_chunk_fts ON document_chunk USING GIN(chunk_fts);

COMMENT ON COLUMN document_chunk.chunk_fts IS 'FTS向量';
