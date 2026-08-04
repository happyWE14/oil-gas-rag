package com.wong.collector.application.service.search;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.wong.collector.application.dto.search.Citation;
import com.wong.collector.infrastructure.persistence.dto.MaterialMetricSearchRow;
import com.wong.collector.infrastructure.persistence.dto.MaterialSearchRow;
import com.wong.collector.infrastructure.persistence.dto.ObservationSearchRow;
import com.wong.collector.infrastructure.persistence.dto.PaperSearchRow;
import com.wong.collector.infrastructure.persistence.mapper.MaterialMetricSearchMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialSearchMapper;
import com.wong.collector.infrastructure.persistence.mapper.ObservationSearchMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperSearchMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchTools {

    private final PaperSearchMapper paperSearchMapper;
    private final MaterialSearchMapper materialSearchMapper;
    private final MaterialMetricSearchMapper materialMetricSearchMapper;
    private final ObservationSearchMapper observationSearchMapper;

    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "(\\w+)\\s*([><=]+)\\s*([\\d.]+)",
            Pattern.CASE_INSENSITIVE
    );

    public List<Citation> searchPapersByKeyword(String query, int limit) {
        List<PaperSearchRow> rows = paperSearchMapper.searchFts(query, null, null, null, limit, 0);
        return rows.stream()
            .map(row -> Citation.paper(
                row.getPaperId(),
                row.getTitle(),
                truncate(row.getTitle(), 200),
                row.getScore() != null ? row.getScore() : 0.0
            ))
            .collect(Collectors.toList());
    }

    public List<Citation> searchMaterialsByKeyword(String query, int limit) {
        List<MaterialSearchRow> rows = materialSearchMapper.searchFts(query, null, limit, 0);
        return rows.stream()
            .map(row -> Citation.material(
                row.getPaperId(),
                row.getMaterial(),
                row.getProperty(),
                row.getValue(),
                row.getUnit(),
                row.getConfidence() != null ? row.getConfidence() : 0.0,
                row.getScore() != null ? row.getScore() : 0.0
            ))
            .collect(Collectors.toList());
    }

    public List<Citation> searchMaterialsByMetric(String query, int limit) {
        MetricQuery parsed = parseMetricQuery(query);
        if (parsed == null) {
            log.debug("Could not parse metric query: {}", query);
            return List.of();
        }

        List<MaterialMetricSearchRow> rows = materialMetricSearchMapper.searchMetrics(
                parsed.metricKey, parsed.operator, parsed.value, null, limit, 0);

        return rows.stream()
            .map(row -> Citation.metric(
                row.getPaperId(),
                row.getMaterialKey(),
                row.getMetricKey(),
                row.getValueNum(),
                row.getUnit(),
                row.getConfidence() != null ? row.getConfidence() : 0.0
            ))
            .collect(Collectors.toList());
    }

    public List<Citation> searchMaterialsByObservation(String query, int limit) {
        List<ObservationSearchRow> rows = observationSearchMapper.searchFts(query, null, limit, 0);

        if (rows.isEmpty()) {
            return searchMaterialsByKeyword(query, limit);
        }

        return rows.stream()
            .map(row -> Citation.observation(
                row.getPaperId(),
                row.getMaterialKey(),
                row.getType(),
                truncate(row.getContent(), 200),
                row.getConfidence() != null ? row.getConfidence() : 0.0,
                row.getScore() != null ? row.getScore() : 0.0
            ))
            .collect(Collectors.toList());
    }

    private MetricQuery parseMetricQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        Matcher matcher = METRIC_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }

        String metricKey = matcher.group(1).toLowerCase().trim();
        String operator = matcher.group(2).trim();
        String valueStr = matcher.group(3).trim();

        if (!isAllowedOperator(operator)) {
            return null;
        }

        try {
            double value = Double.parseDouble(valueStr);
            return new MetricQuery(metricKey, operator, value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAllowedOperator(String op) {
        return switch (op) {
            case ">", "<", ">=", "<=", "=", "==" -> true;
            default -> false;
        };
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private record MetricQuery(String metricKey, String operator, Double value) {}
}
