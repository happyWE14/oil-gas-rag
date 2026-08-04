-- Idempotent patch to enforce strong-consistency cross-source dedup by DOI.
-- Safe behavior:
-- - Does not delete or rewrite data.
-- - Aborts with a clear error if duplicates exist (manual merge/dedupe required).

-- NOTE: Use a standard SQL string literal here (instead of $$...$$) because some DB consoles
-- split statements incorrectly when they don't understand dollar-quoted strings, causing
-- "unterminated dollar-quoted string" errors.
DO '
BEGIN
  IF EXISTS (
    SELECT 1
    FROM paper
    WHERE doi IS NOT NULL
      AND deleted = FALSE
    GROUP BY lower(doi)
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION ''paper has duplicate DOI (case-insensitive) among active rows; merge/dedupe before creating unique index.'';
  END IF;
END;
';

CREATE UNIQUE INDEX IF NOT EXISTS uq_paper_doi_lower_active
ON paper ((lower(doi)))
WHERE doi IS NOT NULL
  AND deleted = FALSE;
