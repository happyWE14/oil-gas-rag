package com.wong.collector.application.workflow.search;

import com.wong.collector.application.service.PaperIngestionService;
import com.wong.collector.domain.paper.model.*;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.infrastructure.config.properties.CoreApiProperties;
import com.wong.collector.infrastructure.external.client.CoreApiClient;
import com.wong.collector.infrastructure.external.core.dto.CoreApiResponse;
import com.wong.collector.infrastructure.external.core.dto.PaperInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CoreSearchExecutor implements SearchExecutor {

    private final CoreApiClient coreApiClient;
    private final PaperIngestionService paperIngestionService;
    private final CoreApiProperties properties;

    @Override
    public SearchProvider supports() {
        return SearchProvider.CORE;
    }

    @Override
    public int importPapers(SearchConfig config) {
        Set<String> importedIds = new HashSet<>();
        SearchPaging paging = SearchPaging.of(config, properties.getMaxResults());
        int pageSize = paging.pageSize();
        int offset = paging.offset();

        var resp = coreApiClient.searchWorks(auth(), config.getQuery(), pageSize, offset, "relevance");
        CoreApiResponse response = resp == null ? null : resp.getResult();
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return 0;
        }
        response.getResults().forEach(info -> saveIfNew(info, importedIds));
        return importedIds.size();
    }

    private void saveIfNew(PaperInfo info, Set<String> importedIds) {
        try {
            Paper paper = toPaper(info);
            PaperIngestionService.IngestionResult result = paperIngestionService.ingest(paper);
            if (!result.createdNew() || importedIds.contains(result.paperId().getValue())) {
                return;
            }
            importedIds.add(result.paperId().getValue());
        } catch (Exception ex) {
            log.warn("skip paper {} due to parse error: {}", info.getId(), ex.getMessage());
        }
    }

    private Paper toPaper(PaperInfo info) {
        String coreId = normalizeCoreId(info.getId());
        PaperId paperId = coreId == null ? PaperId.newId() : PaperId.of(coreId);
        Title title = new Title(info.getTitle() == null ? "Untitled" : info.getTitle());
        List<String> authors = info.getAuthors() == null
            ? List.of("Unknown")
            : info.getAuthors().stream()
                .map(author -> author == null ? null : author.getName())
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.toList());
        if (authors.isEmpty()) {
            authors = List.of("Unknown");
        }
        DOI doi = info.getDoi() == null ? null : new DOI(info.getDoi());
        YearPublished year = info.getYearPublished() == null ? null : new YearPublished(info.getYearPublished());
        String abstractText = info.getAbstractText();
        String pdf = info.getDownloadUrl();
        Integer citations = info.getCitationCount();
        return Paper.create(paperId, title, Authors.of(authors), doi, year, PaperSource.CORE, abstractText, pdf, citations);
    }

    private String normalizeCoreId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        int idx = trimmed.lastIndexOf('/');
        String normalized = idx >= 0 ? trimmed.substring(idx + 1) : trimmed;
        normalized = normalized.trim();
        if (normalized.isBlank() || normalized.length() > 64 || normalized.contains("/")) {
            return null;
        }
        return normalized;
    }

    private String auth() {
        return properties.buildAuthHeader();
    }
}
