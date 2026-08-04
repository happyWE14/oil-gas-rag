package com.wong.collector.application.task.processor;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wong.collector.application.service.PaperIngestionService;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarPaperFactory;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarRelationExpansionService;
import com.wong.collector.application.workflow.semanticscholar.SemanticScholarRelationExpansionService.ExpandedRelations;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperSource;
import com.wong.collector.domain.paper.repository.PaperCitationRepository;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.task.model.TaskRecord;
import com.wong.collector.domain.task.model.TaskType;
import com.wong.collector.infrastructure.config.properties.SemanticScholarRelationExpansionProperties;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarPaperInfo;
import com.wong.collector.infrastructure.persistence.mapper.PaperMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 论文 citation/reference 扩展任务（当前仅支持单跳）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelationExpandTaskProcessor implements TaskProcessor {

    private final PaperRepository paperRepository;
    private final PaperIngestionService paperIngestionService;
    private final PaperCitationRepository paperCitationRepository;
    private final PaperMapper paperMapper;
    private final SemanticScholarRelationExpansionService relationExpansionService;
    private final SemanticScholarPaperFactory paperFactory;
    private final SemanticScholarRelationExpansionProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public TaskType supports() {
        return TaskType.PAPER_RELATION_EXPAND;
    }

    @Override
    public String process(TaskRecord record) throws Exception {
        if (!properties.isEnabled()) {
            return "skipped(disabled)";
        }
        if (record == null || record.getPaperId() == null || record.getPaperId().isBlank()) {
            return "skipped(no_paperId)";
        }

        PaperId seedId = PaperId.of(record.getPaperId());
        Paper seed = paperRepository.findById(seedId).orElse(null);
        if (seed == null) {
            return "skipped(paper_not_found)";
        }

        Params params = parseParams(record.getParameters());
        int limit = resolveLimit(params.limit());
        int depth = Math.max(1, params.depth() == null ? 1 : params.depth());
        int maxDepth = Math.max(1, params.maxDepth() == null ? properties.getMaxDepth() : params.maxDepth());
        if (limit <= 0) {
            return "skipped(limit=0)";
        }

        String lookupId = resolveLookupId(seed);
        if (lookupId == null) {
            return "skipped(no_lookup_id)";
        }

        ExpandedRelations expanded = relationExpansionService.expandOneHop(lookupId, limit);
        Counters counters = new Counters();

        counters.citationsFetched = safeSize(expanded.citationPapers());
        counters.referencesFetched = safeSize(expanded.referencePapers());

        for (SemanticScholarPaperInfo citingInfo : safeList(expanded.citationPapers())) {
            ingestAndLinkCitation(seedId, citingInfo, counters);
        }
        for (SemanticScholarPaperInfo citedInfo : safeList(expanded.referencePapers())) {
            ingestAndLinkReference(seedId, citedInfo, counters);
        }

        long referenceCount = paperCitationRepository.countReferences(seedId);
        paperMapper.updateReferenceCount(seedId.getValue(), safeInt(referenceCount));

        String multiHopHint = depth < maxDepth ? ",multiHop=planned(maxDepth=" + maxDepth + ")" : "";
        return "citations(imported=" + counters.citationsImported + ",linked=" + counters.citationEdgesInserted
            + ",skipped_no_oa=" + counters.skippedCitationNoOpenAccess + ")"
            + ",references(imported=" + counters.referencesImported + ",linked=" + counters.referenceEdgesInserted
            + ",skipped_no_oa=" + counters.skippedReferenceNoOpenAccess + ")"
            + ",referenceCount=" + referenceCount
            + ",depth=" + depth
            + multiHopHint;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Params(Integer depth, Integer maxDepth, Integer limit) {
    }

    private Params parseParams(String raw) {
        if (raw == null || raw.isBlank() || !raw.trim().startsWith("{")) {
            return new Params(1, properties.getMaxDepth(), properties.getLimit());
        }
        try {
            Params parsed = objectMapper.readValue(raw, Params.class);
            return parsed == null ? new Params(1, properties.getMaxDepth(), properties.getLimit()) : parsed;
        } catch (Exception ex) {
            log.debug("invalid relation expand params JSON, fallback to defaults: {}", ex.getMessage());
            return new Params(1, properties.getMaxDepth(), properties.getLimit());
        }
    }

    private int resolveLimit(Integer requested) {
        int fallback = Math.max(0, properties.getLimit());
        if (requested == null) {
            return fallback;
        }
        int value = Math.max(0, requested);
        return fallback == 0 ? value : Math.min(value, fallback);
    }

    private String resolveLookupId(Paper seed) {
        if (seed.getDoi() != null && seed.getDoi().value() != null && !seed.getDoi().value().isBlank()) {
            return "DOI:" + seed.getDoi().value();
        }
        if (seed.getPaperSource() == PaperSource.ARXIV) {
            return "ARXIV:" + seed.getId().getValue();
        }
        if (seed.getPaperSource() == PaperSource.SEMANTIC_SCHOLAR) {
            return seed.getId().getValue();
        }
        return null;
    }

    private void ingestAndLinkCitation(PaperId seedId, SemanticScholarPaperInfo citingInfo, Counters counters) {
        Paper citing;
        try {
            citing = paperFactory.toPaper(citingInfo);
        } catch (Exception ex) {
            if ("openAccessPdf.url is missing".equals(ex.getMessage())) {
                counters.skippedCitationNoOpenAccess++;
                return;
            }
            log.debug("skip citation paper due to parse error: {}", ex.getMessage());
            return;
        }

        PaperIngestionService.IngestionResult ingestion;
        try {
            ingestion = paperIngestionService.ingest(citing);
        } catch (Exception ex) {
            log.debug("skip citation paper due to ingest error: {}", ex.getMessage());
            return;
        }
        if (ingestion.createdNew()) {
            counters.citationsImported++;
        }
        boolean inserted = paperCitationRepository.insertIgnore(ingestion.paperId(), seedId);
        if (inserted) {
            counters.citationEdgesInserted++;
        }
    }

    private void ingestAndLinkReference(PaperId seedId, SemanticScholarPaperInfo citedInfo, Counters counters) {
        Paper cited;
        try {
            cited = paperFactory.toPaper(citedInfo);
        } catch (Exception ex) {
            if ("openAccessPdf.url is missing".equals(ex.getMessage())) {
                counters.skippedReferenceNoOpenAccess++;
                return;
            }
            log.debug("skip reference paper due to parse error: {}", ex.getMessage());
            return;
        }

        PaperIngestionService.IngestionResult ingestion;
        try {
            ingestion = paperIngestionService.ingest(cited);
        } catch (Exception ex) {
            log.debug("skip reference paper due to ingest error: {}", ex.getMessage());
            return;
        }
        if (ingestion.createdNew()) {
            counters.referencesImported++;
        }
        boolean inserted = paperCitationRepository.insertIgnore(seedId, ingestion.paperId());
        if (inserted) {
            counters.referenceEdgesInserted++;
        }
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)value;
    }

    private static int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static final class Counters {
        int citationsFetched;
        int referencesFetched;
        int citationsImported;
        int referencesImported;
        int citationEdgesInserted;
        int referenceEdgesInserted;
        int skippedCitationNoOpenAccess;
        int skippedReferenceNoOpenAccess;
    }
}
