package com.wong.collector.application.service.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.wong.collector.application.dto.search.SearchPlan;
import com.wong.collector.application.dto.search.SearchPlan.SearchStep;

@Service
public class QueryRouter {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+\\.?\\d*\\s*(GPa|MPa|kPa|Pa|eV|meV|keV|MeV|nm|μm|mm|cm|m|K|°C|%|wt%|at%)");
    private static final Pattern MATERIAL_KEYWORDS = Pattern.compile("(?i)(alloy|steel|aluminum|titanium|copper|iron|nickel|cobalt|zinc|ceramic|polymer|composite|oxide|carbide|nitride)");

    public SearchPlan route(String query) {
        List<SearchStep> steps = new ArrayList<>();

        steps.add(SearchStep.FTS_PAPER);

        if (MATERIAL_KEYWORDS.matcher(query).find()) {
            steps.add(SearchStep.FTS_MATERIAL);
        }

        if (NUMBER_PATTERN.matcher(query).find()) {
            steps.add(SearchStep.METRIC_QUERY);
        }

        if (query.length() > 50 || query.contains("?") || query.contains("how") || query.contains("why")) {
            steps.add(SearchStep.OBSERVATION_SEARCH);
        }

        return SearchPlan.of(steps);
    }

    public String describeStep(SearchStep step) {
        return switch (step) {
            case FTS_PAPER -> "Searching papers...";
            case FTS_MATERIAL -> "Searching materials...";
            case METRIC_QUERY -> "Querying metrics...";
            case OBSERVATION_SEARCH -> "Searching observations...";
        };
    }
}
