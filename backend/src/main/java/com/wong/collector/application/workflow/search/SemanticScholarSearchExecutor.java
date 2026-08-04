package com.wong.collector.application.workflow.search;

import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.application.task.NonRetryableTaskException;
import com.wong.collector.application.service.PaperIngestionService;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarApiSupport;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarPaperFactory;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarRequestGate;
import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.event.PaperRelationExpansionRequestedEvent;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.infrastructure.config.properties.SemanticScholarApiProperties;
import com.wong.collector.infrastructure.config.properties.SemanticScholarRelationExpansionProperties;
import com.wong.collector.infrastructure.external.client.SemanticScholarApiClient;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarPaperInfo;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarSearchResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Semantic Scholar 平台论文导入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticScholarSearchExecutor implements SearchExecutor {

    /**
     * 默认返回字段，尽量覆盖后续流水线需要的内容。
     */
    private static final String DEFAULT_FIELDS =
        "paperId,title,abstract,year,authors,citationCount,externalIds,openAccessPdf,url,isOpenAccess";

    private final SemanticScholarApiClient apiClient;
    private final SemanticScholarApiProperties properties;
    private final SemanticScholarRequestGate requestGate;
    private final SemanticScholarPaperFactory paperFactory;
    private final PaperIngestionService paperIngestionService;
    private final EventPublisher eventPublisher;
    private final SemanticScholarRelationExpansionProperties relationExpansionProperties;
    private final ObjectMapper objectMapper;

    @Override
    public SearchProvider supports() {
        return SearchProvider.SEMANTIC_SCHOLAR;
    }

    @Override
    public int importPapers(SearchConfig config) {
        Set<String> importedIds = new HashSet<>();
        Set<String> relationExpandedSeeds = new HashSet<>();
        SearchPaging paging = SearchPaging.of(config, properties.getMaxResults());
        int pageSize = paging.pageSize();
        int offset = paging.offset();
        SearchFilters filters = parseFilters(config.getFilters());
        String fields = mergeFields(DEFAULT_FIELDS, config.getSearchFields());

        requestGate.acquire();
        try {
            ForestResponse<SemanticScholarSearchResponse> resp = filters.openAccessPdfOnly()
                ? apiClient.searchPapers(
                    properties.apiKeyHeaderValue(),
                    config.getQuery(),
                    pageSize,
                    offset,
                    fields,
                    filters.publicationTypes(),
                    filters.minCitationCount(),
                    filters.publicationDateOrYear(),
                    filters.year(),
                    filters.venue(),
                    filters.fieldsOfStudy()
                )
                : apiClient.searchPapersAll(
                    properties.apiKeyHeaderValue(),
                    config.getQuery(),
                    pageSize,
                    offset,
                    fields,
                    filters.publicationTypes(),
                    filters.minCitationCount(),
                    filters.publicationDateOrYear(),
                    filters.year(),
                    filters.venue(),
                    filters.fieldsOfStudy()
                );
            SemanticScholarApiSupport.assertSuccess(resp);
            SemanticScholarSearchResponse body = resp == null ? null : resp.getResult();
            if (body == null) {
                throw new NonRetryableTaskException("Semantic Scholar 响应为空（可能是字段/参数不匹配或解析失败）");
            }
            if (body.getData() == null || body.getData().isEmpty()) {
                return 0;
            }
            body.getData().forEach(info -> saveIfNew(info, importedIds, relationExpandedSeeds));
            return importedIds.size();
        } finally {
            requestGate.release();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SearchFilters(
        Boolean openAccessPdf,
        String publicationTypes,
        String minCitationCount,
        String publicationDateOrYear,
        String year,
        String venue,
        String fieldsOfStudy
    ) {
        boolean openAccessPdfOnly() {
            return openAccessPdf == null || openAccessPdf;
        }
    }

    private SearchFilters parseFilters(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SearchFilters(null, null, null, null, null, null, null);
        }
        String text = raw.trim();
        if (!text.startsWith("{")) {
            log.warn("semantic scholar filters must be JSON, ignored");
            return new SearchFilters(null, null, null, null, null, null, null);
        }
        try {
            return objectMapper.readValue(text, SearchFilters.class);
        } catch (Exception ex) {
            log.warn("invalid semantic scholar filters JSON, ignored: {}", ex.getMessage());
            return new SearchFilters(null, null, null, null, null, null, null);
        }
    }

    private String mergeFields(String base, String extra) {
        if (extra == null || extra.isBlank()) {
            return base;
        }
        Set<String> fields = new LinkedHashSet<>();
        addFields(fields, base);
        addFields(fields, extra);
        return String.join(",", fields);
    }

    private void addFields(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String token : csv.split(",")) {
            if (token == null) {
                continue;
            }
            String field = token.trim();
            if (!field.isBlank()) {
                target.add(field);
            }
        }
    }

    private void saveIfNew(SemanticScholarPaperInfo info, Set<String> importedIds, Set<String> relationExpandedSeeds) {
        try {
            Paper paper = paperFactory.toPaper(info);
            PaperIngestionService.IngestionResult result = paperIngestionService.ingest(paper);
            if (result.createdNew()) {
                importedIds.add(result.paperId().getValue());
            }
            publishRelationExpansionRequestIfEnabled(result.paperId(), relationExpandedSeeds);
        } catch (Exception ex) {
            if ("openAccessPdf.url is missing".equals(ex.getMessage())) {
                log.debug("skip semantic scholar paper {} because open access pdf is missing", info == null ? null : info.getPaperId());
                return;
            }
            log.warn("skip semantic scholar paper {} due to parse error: {}", info == null ? null : info.getPaperId(), ex.getMessage());
        }
    }

    private void publishRelationExpansionRequestIfEnabled(PaperId paperId, Set<String> published) {
        if (paperId == null) {
            return;
        }
        if (!relationExpansionProperties.isEnabled()) {
            return;
        }
        if (published != null && !published.add(paperId.getValue())) {
            return;
        }
        eventPublisher.publish(new PaperRelationExpansionRequestedEvent(paperId.getValue()));
    }
}
