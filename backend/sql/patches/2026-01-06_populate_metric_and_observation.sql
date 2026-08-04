-- ============================================================================
-- Populate material_metric and material_observation from detail_json
-- ============================================================================
-- Run AFTER applying 2026-01-06_search_fts_and_metric_tables.sql
-- Safe to re-run (uses ON CONFLICT DO NOTHING)

-- ============================================================================
-- Step 1: Populate material_metric from core_metrics
-- ============================================================================

WITH metrics_expanded AS (
    SELECT 
        me.paper_id,
        lower(trim(me.material)) AS material_key,
        metric.key AS metric_key,
        elem->>'unit' AS unit,
        elem->'value'->>'type' AS json_value_type,
        (elem->'value'->>'num')::double precision AS value_num,
        (elem->'value'->>'min')::double precision AS value_min,
        (elem->'value'->>'max')::double precision AS value_max,
        elem->'value'->>'raw' AS value_text,
        elem->'value'->>'qualifier' AS qualifier,
        (elem->>'confidence')::numeric(4,3) AS confidence,
        ARRAY(SELECT jsonb_array_elements_text(elem->'chunk_ids')) AS chunk_ids,
        jsonb_strip_nulls((elem->'condition')::jsonb - 'chunk_ids'::text) AS conditions
    FROM material_extraction me,
    LATERAL jsonb_each(me.detail_json->'core_metrics') AS metric(key, arr),
    LATERAL jsonb_array_elements(metric.arr) AS elem
    WHERE me.detail_json IS NOT NULL
      AND me.detail_json->'core_metrics' IS NOT NULL
      AND jsonb_typeof(metric.arr) = 'array'
      AND jsonb_array_length(metric.arr) > 0
)
INSERT INTO material_metric (
    paper_id, material_key, metric_key, value_type, value_num, value_min, value_max, 
    value_text, unit, qualifier, conditions, confidence, chunk_ids
)
SELECT 
    paper_id,
    material_key,
    metric_key,
    CASE 
        WHEN json_value_type = 'number' THEN 'number'
        WHEN json_value_type = 'range' OR (value_min IS NOT NULL AND value_max IS NOT NULL) THEN 'range'
        ELSE 'text'
    END AS value_type,
    value_num,
    value_min,
    value_max,
    value_text,
    unit,
    qualifier,
    coalesce(conditions, '{}'::jsonb),
    confidence,
    chunk_ids
FROM metrics_expanded
ON CONFLICT DO NOTHING;


-- ============================================================================
-- Step 2: Populate material_observation from observations array
-- ============================================================================

WITH observations_expanded AS (
    SELECT 
        me.paper_id,
        lower(trim(me.material)) AS material_key,
        obs->>'type' AS type,
        obs->>'text' AS content,
        (obs->>'confidence')::numeric(4,3) AS confidence,
        ARRAY(SELECT jsonb_array_elements_text(obs->'chunk_ids')) AS chunk_ids
    FROM material_extraction me,
    LATERAL jsonb_array_elements(me.detail_json->'observations') AS obs
    WHERE me.detail_json IS NOT NULL
      AND me.detail_json->'observations' IS NOT NULL
      AND jsonb_typeof(me.detail_json->'observations') = 'array'
      AND jsonb_array_length(me.detail_json->'observations') > 0
)
INSERT INTO material_observation (
    paper_id, material_key, type, content, confidence, chunk_ids
)
SELECT 
    paper_id,
    material_key,
    type,
    content,
    confidence,
    chunk_ids
FROM observations_expanded
WHERE content IS NOT NULL AND content <> ''
ON CONFLICT DO NOTHING;


-- ============================================================================
-- Step 3: Verify results
-- ============================================================================

SELECT 'material_metric' AS table_name, COUNT(*) AS row_count FROM material_metric
UNION ALL
SELECT 'material_observation' AS table_name, COUNT(*) AS row_count FROM material_observation;
