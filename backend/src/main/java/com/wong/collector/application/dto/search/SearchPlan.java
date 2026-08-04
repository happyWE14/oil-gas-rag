package com.wong.collector.application.dto.search;

import java.util.List;

public record SearchPlan(List<SearchStep> steps) {

    public enum SearchStep {
        FTS_PAPER,
        FTS_MATERIAL,
        METRIC_QUERY,
        OBSERVATION_SEARCH
    }

    public static SearchPlan of(SearchStep... steps) {
        return new SearchPlan(List.of(steps));
    }

    public static SearchPlan of(List<SearchStep> steps) {
        return new SearchPlan(List.copyOf(steps));
    }
}
