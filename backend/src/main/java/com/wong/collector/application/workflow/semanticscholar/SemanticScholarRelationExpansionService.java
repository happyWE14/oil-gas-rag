package com.wong.collector.application.workflow.semanticscholar;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.dtflys.forest.http.ForestResponse;
import com.wong.collector.infrastructure.config.properties.SemanticScholarApiProperties;
import com.wong.collector.infrastructure.external.client.SemanticScholarApiClient;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarCitation;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarCitationBatch;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarPaperInfo;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarReference;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarReferenceBatch;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SemanticScholarRelationExpansionService {

    private static final String CITATION_FIELDS =
        "citingPaper.paperId,citingPaper.title,citingPaper.abstract,citingPaper.year,citingPaper.authors,"
            + "citingPaper.citationCount,citingPaper.externalIds,citingPaper.openAccessPdf,citingPaper.url,citingPaper.isOpenAccess";

    private static final String REFERENCE_FIELDS =
        "citedPaper.paperId,citedPaper.title,citedPaper.abstract,citedPaper.year,citedPaper.authors,"
            + "citedPaper.citationCount,citedPaper.externalIds,citedPaper.openAccessPdf,citedPaper.url,citedPaper.isOpenAccess";

    private final SemanticScholarApiClient apiClient;
    private final SemanticScholarApiProperties properties;
    private final SemanticScholarRequestGate requestGate;

    public record ExpandedRelations(List<SemanticScholarPaperInfo> citationPapers,
                                   List<SemanticScholarPaperInfo> referencePapers) {
    }

    public ExpandedRelations expandOneHop(String paperId, int limitPerDirection) {
        int limit = Math.max(0, limitPerDirection);
        if (paperId == null || paperId.isBlank() || limit == 0) {
            return new ExpandedRelations(List.of(), List.of());
        }
        List<SemanticScholarPaperInfo> citations = fetchCitations(paperId, limit);
        List<SemanticScholarPaperInfo> references = fetchReferences(paperId, limit);
        return new ExpandedRelations(citations, references);
    }

    private List<SemanticScholarPaperInfo> fetchCitations(String paperId, int limit) {
        List<SemanticScholarPaperInfo> result = new ArrayList<>();
        Integer offset = 0;
        while (offset != null && result.size() < limit) {
            int batchSize = Math.min(limit - result.size(), 100);
            requestGate.acquire();
            try {
                ForestResponse<SemanticScholarCitationBatch> resp = apiClient.getCitations(
                    properties.apiKeyHeaderValue(),
                    paperId,
                    null,
                    offset,
                    batchSize,
                    CITATION_FIELDS
                );
                SemanticScholarApiSupport.assertSuccess(resp);
                SemanticScholarCitationBatch body = resp == null ? null : resp.getResult();
                if (body == null || body.getData() == null || body.getData().isEmpty()) {
                    break;
                }
                for (SemanticScholarCitation citation : body.getData()) {
                    if (citation == null || citation.getCitingPaper() == null) {
                        continue;
                    }
                    result.add(citation.getCitingPaper());
                    if (result.size() >= limit) {
                        break;
                    }
                }
                offset = body.getNext();
            } finally {
                requestGate.release();
            }
        }
        return result;
    }

    private List<SemanticScholarPaperInfo> fetchReferences(String paperId, int limit) {
        List<SemanticScholarPaperInfo> result = new ArrayList<>();
        Integer offset = 0;
        while (offset != null && result.size() < limit) {
            int batchSize = Math.min(limit - result.size(), 100);
            requestGate.acquire();
            try {
                ForestResponse<SemanticScholarReferenceBatch> resp = apiClient.getReferences(
                    properties.apiKeyHeaderValue(),
                    paperId,
                    null,
                    offset,
                    batchSize,
                    REFERENCE_FIELDS
                );
                SemanticScholarApiSupport.assertSuccess(resp);
                SemanticScholarReferenceBatch body = resp == null ? null : resp.getResult();
                if (body == null || body.getData() == null || body.getData().isEmpty()) {
                    break;
                }
                for (SemanticScholarReference reference : body.getData()) {
                    if (reference == null || reference.getCitedPaper() == null) {
                        continue;
                    }
                    result.add(reference.getCitedPaper());
                    if (result.size() >= limit) {
                        break;
                    }
                }
                offset = body.getNext();
            } finally {
                requestGate.release();
            }
        }
        return result;
    }
}

