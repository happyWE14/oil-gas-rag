DROP TABLE IF EXISTS paper_download;
DROP TABLE IF EXISTS paper_transform;
DROP TABLE IF EXISTS paper_relevant;
DROP TABLE IF EXISTS model_output_log;
DROP TABLE IF EXISTS document_chunk;
DROP TABLE IF EXISTS material_extraction;
DROP TABLE IF EXISTS paper_citation;
DROP TABLE IF EXISTS paper;
DROP TABLE IF EXISTS task_record;
DROP TABLE IF EXISTS paper_search_config;

CREATE TABLE paper (
    paper_id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    authors VARCHAR(2000) NOT NULL,
    doi VARCHAR(255),
    year_published INT,
    paper_source VARCHAR(50) NOT NULL,
    abstract_content TEXT,
    download_url VARCHAR(1024),
    citation_count INT,
    reference_count INT,
    status VARCHAR(50) NOT NULL,
    retry_count INT DEFAULT 0,
    error_message VARCHAR(500),
    version BIGINT DEFAULT 0,
    deleted INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Best-effort DOI uniqueness for tests (H2/MySQL mode differs from Postgres case-insensitive partial index).
CREATE UNIQUE INDEX uq_paper_doi ON paper(doi);

CREATE TABLE paper_citation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    citing_paper_id VARCHAR(64) NOT NULL,
    cited_paper_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_paper_citation_citing_cited UNIQUE (citing_paper_id, cited_paper_id),
    CONSTRAINT ck_paper_citation_not_self CHECK (citing_paper_id <> cited_paper_id),
    CONSTRAINT fk_paper_citation_citing FOREIGN KEY (citing_paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE,
    CONSTRAINT fk_paper_citation_cited FOREIGN KEY (cited_paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE paper_download (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    source_url VARCHAR(1024),
    oss_url VARCHAR(1024),
    file_size BIGINT,
    try_times INT DEFAULT 0,
    download_status VARCHAR(32) NOT NULL,
    error_message VARCHAR(500),
    start_time TIMESTAMP,
    finish_time TIMESTAMP,
    CONSTRAINT fk_download_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE paper_transform (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    transform_provider VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL,
    markdown_path VARCHAR(1024),
    error_message VARCHAR(500),
    start_time TIMESTAMP,
    finish_time TIMESTAMP,
    CONSTRAINT fk_transform_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE paper_relevant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    is_relevant BOOLEAN NOT NULL,
    score DOUBLE,
    analyzer VARCHAR(100),
    reason TEXT,
    raw_result CLOB,
    keywords CLOB,
    threshold_used DOUBLE,
    has_extractable_material_info BOOLEAN,
    CONSTRAINT fk_relevant_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE model_output_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    model_name VARCHAR(100),
    model_output CLOB NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_model_output_paper_task (paper_id, task_type),
    CONSTRAINT fk_model_output_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE document_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    order_no INT NOT NULL,
    content CLOB NOT NULL,
    embedding_model VARCHAR(100),
    vector CLOB,
    CONSTRAINT uq_chunk_paper_order_no UNIQUE (paper_id, order_no),
    CONSTRAINT fk_chunk_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE material_extraction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64) NOT NULL,
    material VARCHAR(255) NOT NULL,
    property VARCHAR(255),
    method VARCHAR(255),
    confidence DOUBLE,
    detail_json CLOB,
    CONSTRAINT fk_material_paper FOREIGN KEY (paper_id) REFERENCES paper(paper_id) ON DELETE CASCADE
);

CREATE TABLE task_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id VARCHAR(64),
    task_type VARCHAR(64) NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    description TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    error_message CLOB,
    error_category VARCHAR(20),
    error_reason VARCHAR(32),
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    next_retry_at TIMESTAMP,
    parameters CLOB,
    result CLOB,
    created_by VARCHAR(100) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paper_search_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(64) NOT NULL,
    query VARCHAR(1024) NOT NULL,
    current_page INT NOT NULL,
    page_size INT NOT NULL,
    total_page INT,
    total_count INT,
    search_fields VARCHAR(500),
    sort_order VARCHAR(100),
    filters TEXT,
    search_status VARCHAR(32),
    last_run_time TIMESTAMP,
    next_run_time TIMESTAMP,
    cron_expression VARCHAR(100),
    max_empty_pages INT,
    empty_page_count INT,
    last_failure_reason VARCHAR(500),
    query_snapshot CLOB,
    keywords_snapshot CLOB,
    template_name VARCHAR(100),
    batch_id VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
