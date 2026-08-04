-- Idempotent patch to support stable document_chunk ids via upsert target (paper_id, order_no).
-- Safe behavior:
-- - Does not delete or rewrite data.
-- - Aborts with a clear error if duplicates exist (manual dedupe required).

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM document_chunk
    GROUP BY paper_id, order_no
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'document_chunk has duplicate (paper_id, order_no); dedupe before creating unique index.';
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_chunk_paper_order_no ON document_chunk (paper_id, order_no);

