-- Idempotent patch to support citation/reference graph expansion.
-- Safe behavior:
-- - Adds nullable columns and a new relation table only.
-- - Does not delete or rewrite data.

ALTER TABLE IF EXISTS paper
  ADD COLUMN IF NOT EXISTS reference_count INTEGER NULL;

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

CREATE UNIQUE INDEX IF NOT EXISTS uq_paper_citation_citing_cited ON paper_citation (citing_paper_id, cited_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_citation_citing ON paper_citation (citing_paper_id);
CREATE INDEX IF NOT EXISTS idx_paper_citation_cited ON paper_citation (cited_paper_id);
