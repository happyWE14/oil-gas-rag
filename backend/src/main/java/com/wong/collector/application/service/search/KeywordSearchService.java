package com.wong.collector.application.service.search;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wong.collector.application.dto.request.KeywordSearchRequest;
import com.wong.collector.application.dto.response.KeywordSearchResponse;
import com.wong.collector.application.dto.response.KeywordSearchResponse.MaterialHit;
import com.wong.collector.application.dto.response.KeywordSearchResponse.Metadata;
import com.wong.collector.application.dto.response.KeywordSearchResponse.PaperHit;
import com.wong.collector.application.dto.response.KeywordSearchResponse.Results;
import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.infrastructure.persistence.dto.MaterialSearchRow;
import com.wong.collector.infrastructure.persistence.dto.PaperSearchRow;
import com.wong.collector.infrastructure.persistence.mapper.MaterialSearchMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperSearchMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeywordSearchService {

    private final PaperSearchMapper paperSearchMapper;
    private final MaterialSearchMapper materialSearchMapper;

    @Transactional(readOnly = true)
    public KeywordSearchResponse search(KeywordSearchRequest request) {
        long startTime = System.currentTimeMillis();

        String query = request.query();
        var filters = request.filters();
        var pagination = request.pagination();

        int page = Math.max(1, pagination.page());
        int size = Math.min(Math.max(1, pagination.size()), 100);
        long offset = (long) (page - 1) * size;

        PageResult<PaperHit> paperResults = PageResult.empty(page, size);
        PageResult<MaterialHit> materialResults = PageResult.empty(page, size);

        String type = filters.type();
        boolean searchPapers = "all".equals(type) || "paper".equals(type);
        boolean searchMaterials = "all".equals(type) || "material".equals(type);

        if (searchPapers) {
            paperResults = searchPapers(query, filters, page, size, offset);
        }

        if (searchMaterials) {
            materialResults = searchMaterials(query, filters, page, size, offset);
        }

        long searchTimeMs = System.currentTimeMillis() - startTime;

        return new KeywordSearchResponse(
            query,
            new Results(paperResults, materialResults),
            new Metadata(searchTimeMs)
        );
    }

    private PageResult<PaperHit> searchPapers(String query, KeywordSearchRequest.Filters filters,
                                               int page, int size, long offset) {
        List<String> states = filters.paperState();
        Integer yearMin = filters.yearRange() != null ? filters.yearRange().min() : null;
        Integer yearMax = filters.yearRange() != null ? filters.yearRange().max() : null;

        Long total = paperSearchMapper.countFtsResults(query, states, yearMin, yearMax);
        if (total == null || total == 0) {
            return PageResult.empty(page, size);
        }

        List<PaperSearchRow> rows = paperSearchMapper.searchFts(query, states, yearMin, yearMax, size, offset);
        List<PaperHit> hits = rows.stream()
            .map(row -> new PaperHit(
                row.getPaperId(),
                row.getTitle(),
                row.getAuthors(),
                row.getYearPublished(),
                row.getPaperSource(),
                row.getStatus(),
                row.getScore() != null ? row.getScore() : 0.0
            ))
            .toList();

        return PageResult.of(hits, page, size, total);
    }

    private PageResult<MaterialHit> searchMaterials(String query, KeywordSearchRequest.Filters filters,
                                                     int page, int size, long offset) {
        Double minConfidence = filters.minConfidence();

        Long total = materialSearchMapper.countFtsResults(query, minConfidence);
        if (total == null || total == 0) {
            return PageResult.empty(page, size);
        }

        List<MaterialSearchRow> rows = materialSearchMapper.searchFts(query, minConfidence, size, offset);
        List<MaterialHit> hits = rows.stream()
            .map(row -> new MaterialHit(
                row.getPaperId(),
                row.getMaterial(),
                row.getProperty(),
                row.getConfidence(),
                row.getScore() != null ? row.getScore() : 0.0
            ))
            .toList();

        return PageResult.of(hits, page, size, total);
    }
}
