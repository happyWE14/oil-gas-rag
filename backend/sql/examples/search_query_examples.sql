-- ============================================================================
-- Search Query Examples
-- ============================================================================
-- Run AFTER applying DDL and data population patches.

-- ============================================================================
-- Scenario 1: Keyword Search - Papers
-- "Find papers about HPAM viscosity"
-- ============================================================================
SELECT 
    paper_id,
    title,
    ts_rank(paper_fts, query) AS rank
FROM paper, plainto_tsquery('simple', 'HPAM viscosity') AS query
WHERE paper_fts @@ query
  AND deleted = FALSE
ORDER BY rank DESC
LIMIT 10;


-- ============================================================================
-- Scenario 2: Keyword Search - Materials
-- "Find materials related to fracturing fluid"
-- ============================================================================
SELECT 
    me.paper_id,
    me.material,
    me.property,
    p.title AS paper_title,
    ts_rank(me.material_fts, query) AS rank
FROM material_extraction me
JOIN paper p ON me.paper_id = p.paper_id,
     plainto_tsquery('simple', 'fracturing fluid') AS query
WHERE me.material_fts @@ query
ORDER BY rank DESC
LIMIT 10;


-- ============================================================================
-- Scenario 3: Range Query - Viscosity > 500 mPa·s
-- "Find materials with apparent viscosity greater than 500 mPa·s"
-- ============================================================================
SELECT 
    mm.paper_id,
    mm.material_key,
    mm.value_num AS viscosity,
    mm.unit,
    mm.conditions,
    p.title AS paper_title
FROM material_metric mm
JOIN paper p ON mm.paper_id = p.paper_id
WHERE mm.metric_key = 'apparent_viscosity_mPa_s'
  AND mm.value_type = 'number'
  AND mm.value_num > 500
ORDER BY mm.value_num DESC
LIMIT 20;


-- ============================================================================
-- Scenario 4: Condition-Filtered Query
-- "Find viscosity measurements at shear rate 170 s⁻¹"
-- ============================================================================
SELECT 
    mm.paper_id,
    mm.material_key,
    mm.value_num AS viscosity,
    mm.conditions->>'concentration_pct_wt' AS concentration,
    mm.conditions->>'temperature_C' AS temperature,
    p.title AS paper_title
FROM material_metric mm
JOIN paper p ON mm.paper_id = p.paper_id
WHERE mm.metric_key = 'apparent_viscosity_mPa_s'
  AND mm.conditions @> '{"shear_rate_s_1": 170}'::jsonb
ORDER BY mm.value_num DESC;


-- ============================================================================
-- Scenario 5: Semantic Search in Observations
-- "Find formulation information about CTAB"
-- ============================================================================
SELECT 
    mo.paper_id,
    mo.material_key,
    mo.type,
    mo.content,
    p.title AS paper_title,
    ts_rank(mo.content_fts, query) AS rank
FROM material_observation mo
JOIN paper p ON mo.paper_id = p.paper_id,
     plainto_tsquery('simple', 'CTAB formulation') AS query
WHERE mo.content_fts @@ query
ORDER BY rank DESC
LIMIT 10;


-- ============================================================================
-- Scenario 6: Combined Query
-- "Find all metrics and observations for a specific material"
-- ============================================================================
WITH target_material AS (
    SELECT 'ctab' AS material_key
)
SELECT 
    'metric' AS source,
    mm.paper_id,
    mm.metric_key AS info_type,
    mm.value_num::text || ' ' || coalesce(mm.unit, '') AS info_value,
    mm.conditions::text AS conditions,
    p.title AS paper_title
FROM material_metric mm
JOIN paper p ON mm.paper_id = p.paper_id
WHERE mm.material_key = (SELECT material_key FROM target_material)

UNION ALL

SELECT 
    'observation' AS source,
    mo.paper_id,
    mo.type AS info_type,
    mo.content AS info_value,
    NULL AS conditions,
    p.title AS paper_title
FROM material_observation mo
JOIN paper p ON mo.paper_id = p.paper_id
WHERE mo.material_key = (SELECT material_key FROM target_material)

ORDER BY source, paper_id;
