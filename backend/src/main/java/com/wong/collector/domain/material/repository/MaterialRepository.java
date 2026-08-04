package com.wong.collector.domain.material.repository;

import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.material.model.MaterialGlobalStats;
import com.wong.collector.domain.material.model.MaterialPaperOccurrence;
import com.wong.collector.domain.material.model.MaterialSummary;


import com.wong.collector.domain.material.model.MaterialGlobalStats;

/**
 * Material-centric query repository.
 */
public interface MaterialRepository {

    PageResult<MaterialSummary> page(MaterialQuery query);

    PageResult<MaterialPaperOccurrence> pagePapers(MaterialPaperQuery query);

    MaterialGlobalStats getGlobalStats();
}

