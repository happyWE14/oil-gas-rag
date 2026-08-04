package com.wong.collector.application.workflow.search;

import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchProvider;

public interface SearchExecutor {

    SearchProvider supports();

    int importPapers(SearchConfig config);
}
