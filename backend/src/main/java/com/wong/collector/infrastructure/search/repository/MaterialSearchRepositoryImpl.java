package com.wong.collector.infrastructure.search.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.NestedQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;
import com.wong.collector.domain.search.model.MaterialSearchCriteria;
import com.wong.collector.domain.search.repository.MaterialSearchRepository;
import com.wong.collector.infrastructure.search.document.MaterialEsDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MaterialSearchRepositoryImpl implements MaterialSearchRepository {

    private final ElasticsearchClient esClient;
    private static final String INDEX_NAME = "materials";

    @Override
    public void bulkSave(List<MaterialEsDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            log.warn("No documents to save");
            return;
        }

        log.info("Starting bulk save of {} documents to index '{}'", documents.size(), INDEX_NAME);

        long uniqueCount = documents.stream()
                .map(MaterialEsDocument::getDocId)
                .distinct()
                .count();
        if (uniqueCount != documents.size()) {
            log.warn("Found duplicate docIds in batch, total: {}, unique: {}", documents.size(), uniqueCount);
            documents = documents.stream()
                    .collect(Collectors.toMap(
                            MaterialEsDocument::getDocId,
                            d -> d,
                            (existing, replacement) -> existing
                    ))
                    .values()
                    .stream()
                    .collect(Collectors.toList());
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (MaterialEsDocument doc : documents) {
                if (doc.getDocId() == null || doc.getDocId().isEmpty()) {
                    log.error("Document with null/empty docId, skipping. Material: {}",
                            doc.getMaterial() != null ? doc.getMaterial().getMaterialName() : "unknown");
                    continue;
                }

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(doc.getDocId())
                                .document(doc)
                        )
                );
            }

            BulkResponse response = esClient.bulk(bulkBuilder.build());

            if (response.errors()) {
                long errorCount = response.items().stream()
                        .filter(item -> item.error() != null)
                        .count();

                log.error("Bulk save completed with {} errors out of {} documents",
                        errorCount, documents.size());

                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("ES Bulk Error - ID: {}, Reason: {}, Type: {}",
                                item.id(),
                                item.error().reason(),
                                item.error().type());
                    }
                }

                throw new RuntimeException(String.format(
                        "Bulk save failed with %d errors. First error: %s",
                        errorCount,
                        response.items().stream()
                                .filter(item -> item.error() != null)
                                .findFirst()
                                .map(item -> item.error().reason())
                                .orElse("Unknown error")
                ));
            }

            log.info("Successfully saved {} documents to ES", documents.size());

        } catch (IOException e) {
            log.error("IOException during bulk save to ES: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save documents to ES", e);
        } catch (Exception e) {
            log.error("Unexpected error during bulk save: {}", e.getMessage(), e);
            throw new RuntimeException("Bulk error", e);
        }
    } @Override
    public void saveAll(List<MaterialEsDocument> documents) {
        if (documents.isEmpty()) return;

        BulkRequest.Builder br = new BulkRequest.Builder();

        for (MaterialEsDocument doc : documents) {
            br.operations(op -> op
                    .index(idx -> idx
                            .index(INDEX_NAME)
                            .id(doc.getDocId())
                            .document(doc)
                    )
            );
        }

        try {
            BulkResponse response = esClient.bulk(br.build());
            if (response.errors()) {
                log.error("Bulk save errors: {}", response.items().stream()
                        .filter(i -> i.error() != null)
                        .map(i -> i.error().reason())
                        .collect(Collectors.toList()));
            }
        } catch (IOException e) {
            throw new RuntimeException("ES批量保存失败", e);
        }
    }

    @Override
    public Page<MaterialEsDocument> search(MaterialSearchCriteria criteria) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            // 1. 材料名称搜索
            if (criteria.getMaterialName() != null && !criteria.getMaterialName().isEmpty()) {
                boolBuilder.must(MatchQuery.of(m -> m
                        .field("material.material_name")
                        .query(criteria.getMaterialName())
                )._toQuery());
            }

            // 2. 论文标题搜索
            if (criteria.getPaperTitle() != null && !criteria.getPaperTitle().isEmpty()) {
                boolBuilder.must(MatchQuery.of(m -> m
                        .field("paper.title")
                        .query(criteria.getPaperTitle())
                )._toQuery());
            }

            // 3. 指标 key 过滤
            if (criteria.getMetricKey() != null && !criteria.getMetricKey().isEmpty()) {
                boolBuilder.must(NestedQuery.of(n -> n
                        .path("metrics")
                        .query(q -> q.match(m -> m
                                .field("metrics.metric_key")
                                .query(criteria.getMetricKey())
                        ))
                )._toQuery());
            }

            // 4. 年份范围 - 使用 JsonData.of() 包装数值
            if (criteria.getYearFrom() != null || criteria.getYearTo() != null) {
                RangeQuery.Builder rangeBuilder = new RangeQuery.Builder()
                        .field("paper.year");
                if (criteria.getYearFrom() != null) {
                    rangeBuilder.gte(JsonData.of(criteria.getYearFrom()));
                }
                if (criteria.getYearTo() != null) {
                    rangeBuilder.lte(JsonData.of(criteria.getYearTo()));
                }
                boolBuilder.must(rangeBuilder.build()._toQuery());
            }

            // 5. 置信度范围
            if (criteria.getMinConfidence() != null) {
                boolBuilder.must(RangeQuery.of(r -> r
                        .field("material.confidence")
                        .gte(JsonData.of(criteria.getMinConfidence()))
                )._toQuery());
            }

            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(INDEX_NAME)
                    .query(boolBuilder.build()._toQuery())
                    .from(criteria.getPage() * criteria.getSize())
                    .size(criteria.getSize());

            // 添加排序
            if (criteria.getSortField() != null && !criteria.getSortField().isEmpty()) {
                SortOrder order = "asc".equalsIgnoreCase(criteria.getSortDirection())
                        ? SortOrder.Asc : SortOrder.Desc;

                searchBuilder.sort(SortOptions.of(s -> s
                        .field(FieldSort.of(f -> f
                                .field(criteria.getSortField())
                                .order(order)
                        ))
                ));
            } else {
                searchBuilder.sort(SortOptions.of(s -> s
                        .field(FieldSort.of(f -> f
                                .field("_score")
                                .order(SortOrder.Desc)
                        ))
                ));
            }

            SearchResponse<MaterialEsDocument> response = esClient.search(
                    searchBuilder.build(),
                    MaterialEsDocument.class
            );

            List<MaterialEsDocument> documents = response.hits().hits().stream()
                    .map(hit -> {
                        MaterialEsDocument doc = hit.source();
                        if (doc != null && hit.id() != null) {
                            doc.setDocId(hit.id());
                        }
                        return doc;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null
                    ? response.hits().total().value()
                    : 0;

            Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize());
            return new PageImpl<>(documents, pageable, totalHits);

        } catch (IOException e) {
            log.error("Search failed: {}", e.getMessage(), e);
            throw new RuntimeException("Search operation failed", e);
        }
    }

    @Override
    public List<String> suggestKeywords(String keyword, int size) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }

        try {
            SearchResponse<MaterialEsDocument> response = esClient.search(s -> s
                            .index(INDEX_NAME)
                            .query(q -> q
                                    .matchPhrasePrefix(m -> m
                                            .field("material.material_name")
                                            .query(keyword)
                                    )
                            )
                            .size(size)
                            .source(src -> src
                                    .filter(f -> f.includes("material.material_name"))
                            ),
                    MaterialEsDocument.class
            );

            return response.hits().hits().stream()
                    .map(hit -> {
                        if (hit.source() != null && hit.source().getMaterial() != null) {
                            return hit.source().getMaterial().getMaterialName();
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("Suggest failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public void save(MaterialEsDocument document) {
        if (document == null || document.getDocId() == null) {
            log.error("Cannot save null document or document without ID");
            return;
        }

        try {
            esClient.index(i -> i
                    .index(INDEX_NAME)
                    .id(document.getDocId())
                    .document(document)
            );
            log.debug("Saved document {} to ES", document.getDocId());
        } catch (IOException e) {
            log.error("Failed to save document {}: {}", document.getDocId(), e.getMessage());
            throw new RuntimeException("Failed to save document", e);
        }
    }
}
