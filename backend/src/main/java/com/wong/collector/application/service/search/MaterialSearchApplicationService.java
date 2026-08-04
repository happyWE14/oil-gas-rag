package com.wong.collector.application.service.search;

import com.wong.collector.application.dto.request.MaterialSearchRequest;
import com.wong.collector.application.dto.response.MaterialSearchResponse;
import com.wong.collector.domain.search.model.MaterialSearchCriteria;
import com.wong.collector.domain.search.repository.MaterialSearchRepository;
import com.wong.collector.infrastructure.persistence.repository.PaperRepository;
import com.wong.collector.infrastructure.search.document.MaterialEsDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialSearchApplicationService {

    private final MaterialSearchRepository searchRepository;
    private final MaterialSearchService materialSearchService;
    private final PaperRepository paperRepository;

    /**
     * 材料高级搜索（支持文本、向量、混合搜索）
     */
    public Page<MaterialSearchResponse> searchMaterials(MaterialSearchRequest request) {
        // 构建搜索条件
        MaterialSearchCriteria criteria = new MaterialSearchCriteria();

        // 基础文本过滤
        criteria.setMaterialName(request.getMaterialName());
        criteria.setPaperTitle(request.getPaperTitle());
        criteria.setMetricKey(request.getMetricKey());

        // 向量搜索参数
        if (request.getQueryVector() != null && request.getQueryVector().length > 0) {
            criteria.setQueryVector(request.getQueryVector());
        } else if (request.getQueryText() != null && !request.getQueryText().isEmpty()) {
            // 如果前端只传了文本，记录日志（实际应在Controller层或专门服务转换为向量）
            log.debug("Query text provided but vector is null, text: {}", request.getQueryText());
            // 注意：生产环境这里应该调用Embedding服务将text转为vector
        }

        // 权重设置（默认 0.7:0.3）
        criteria.setVectorWeight(request.getVectorWeight() != null ? request.getVectorWeight() : 0.7);
        criteria.setTextWeight(request.getTextWeight() != null ? request.getTextWeight() : 0.3);

        // 指标范围过滤
        if (request.getMetricRange() != null) {
            MaterialSearchCriteria.MetricRange range = new MaterialSearchCriteria.MetricRange();
            range.setMin(request.getMetricRange().getMin());
            range.setMax(request.getMetricRange().getMax());
            criteria.setMetricRange(range);
        }

        // 年份范围
        criteria.setYearFrom(request.getYearFrom());
        criteria.setYearTo(request.getYearTo());

        // 分页（Spring Data 使用 0-based）
        criteria.setPage(request.getPage());
        criteria.setSize(request.getSize());

        // 排序逻辑
        if ("confidence".equals(request.getSortBy())) {
            criteria.setSortField("material.confidence");
            criteria.setSortDirection("desc");
        } else if ("year".equals(request.getSortBy())) {
            criteria.setSortField("paper.year");
            criteria.setSortDirection("desc");
        } else {
            // relevance 默认使用 _score，无需设置 sortField
            criteria.setSortDirection("desc");
        }

        // 执行搜索
        Page<MaterialEsDocument> result = searchRepository.search(criteria);

        // 转换为DTO
        List<MaterialSearchResponse> responses = result.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(responses, result.getPageable(), result.getTotalElements());
    }

    /**
     * 关键词自动补全（IK分词）
     */
    public List<String> suggest(String keyword, int size) {
        return searchRepository.suggestKeywords(keyword, size);
    }

    /**
     * 根据论文ID同步材料到ES（手动触发-单篇）
     */
    public void syncByPaperId(String paperId) {
        materialSearchService.syncByPaperId(paperId);
    }

    /**
     * 全量同步所有材料到ES（异步批量处理）
     * 解决前端 /api/search/materials/sync-all 404 问题
     */
    public Map<String, Object> syncAllMaterials() {
        log.info("Starting sync...");

        List<String> ids = paperRepository.findAllPaperIds();
        // 添加这行调试，看实际返回多少
        log.info("Total papers found: {}", ids.size());
        if (ids.isEmpty()) {
            log.warn("No papers found to sync");
            return Map.of("total", 0, "success", 0, "failed", 0);
        }

        log.info("Found {} papers to sync", ids.size());
        int success = 0, fail = 0;

        // 每10条输出一次进度，别等全部跑完
        for (int i = 0; i < ids.size(); i++) {
            String paperId = ids.get(i);
            try {
                long start = System.currentTimeMillis();
                materialSearchService.syncByPaperId(paperId);
                long cost = System.currentTimeMillis() - start;

                success++;

                // 每10条或每1秒输出一次进度
                if (i % 10 == 0 || cost > 1000) {
                    log.info("Progress: {}/{} (current: {}ms, success: {}, fail: {})",
                            i, ids.size(), cost, success, fail);
                }

                // 每50条休息一下，防止ES压力过大
                if (i % 50 == 0 && i > 0) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                fail++;
                log.error("Failed {}: {}", paperId, e.getMessage());
            }
        }

        log.info("Sync completed. Total: {}, Success: {}, Failed: {}", ids.size(), success, fail);
        return Map.of("total", ids.size(), "success", success, "failed", fail);
    }
    /**
     * ES文档转换为响应DTO
     */
    private MaterialSearchResponse toResponse(MaterialEsDocument doc) {
        MaterialSearchResponse resp = new MaterialSearchResponse();
        resp.setDocId(doc.getDocId());

        // Paper 信息转换
        if (doc.getPaper() != null) {
            MaterialSearchResponse.PaperInfo paper = new MaterialSearchResponse.PaperInfo();
            paper.setPaperId(doc.getPaper().getPaperId());
            paper.setTitle(doc.getPaper().getTitle());
            paper.setAuthors(doc.getPaper().getAuthors());
            paper.setYear(doc.getPaper().getYear());
            paper.setDoi(doc.getPaper().getDoi());
            paper.setAbstractContent(doc.getPaper().getAbstractContent());
            resp.setPaper(paper);
        }

        // Material 信息转换
        if (doc.getMaterial() != null) {
            MaterialSearchResponse.MaterialInfo mat = new MaterialSearchResponse.MaterialInfo();
            mat.setMaterialKey(doc.getMaterial().getMaterialKey());
            mat.setMaterialName(doc.getMaterial().getMaterialName());
            mat.setConfidence(doc.getMaterial().getConfidence());
            resp.setMaterial(mat);
        }

        // Metrics 转换（嵌套对象）
        if (doc.getMetrics() != null && !doc.getMetrics().isEmpty()) {
            List<MaterialSearchResponse.MetricInfo> metrics = doc.getMetrics().stream()
                    .map(m -> {
                        MaterialSearchResponse.MetricInfo mi = new MaterialSearchResponse.MetricInfo();
                        mi.setMetricKey(m.getMetricKey());
                        mi.setMetricName(m.getMetricName());
                        mi.setValueType(m.getValueType());
                        mi.setValueNum(m.getValueNum());
                        mi.setValueMin(m.getValueMin());
                        mi.setValueMax(m.getValueMax());
                        mi.setValueText(m.getValueText());
                        mi.setUnit(m.getUnit());
                        mi.setConditions((Map<String, Object>) m.getConditions());
                        mi.setConfidence(m.getConfidence());
                        return mi;
                    })
                    .collect(Collectors.toList());
            resp.setMetrics(metrics);
        }

        // Observations 转换（嵌套对象）
        if (doc.getObservations() != null && !doc.getObservations().isEmpty()) {
            List<MaterialSearchResponse.ObservationInfo> observations = doc.getObservations().stream()
                    .map(o -> {
                        MaterialSearchResponse.ObservationInfo oi = new MaterialSearchResponse.ObservationInfo();
                        oi.setType(o.getType());
                        oi.setContent(o.getContent());
                        oi.setConfidence(o.getConfidence());
                        return oi;
                    })
                    .collect(Collectors.toList());
            resp.setObservations(observations);
        }

        // Chunks 转换（如果有）
        if (doc.getChunks() != null && !doc.getChunks().isEmpty()) {
            List<MaterialSearchResponse.ChunkInfo> chunks = doc.getChunks().stream()
                    .map(c -> {
                        MaterialSearchResponse.ChunkInfo ci = new MaterialSearchResponse.ChunkInfo();
                        ci.setChunkId(c.getChunkId());
                        ci.setContent(c.getContent());
                        ci.setOrderNo(c.getOrderNo());
                        return ci;
                    })
                    .collect(Collectors.toList());
            resp.setChunks(chunks);
        }

        // 向量信息（调试用，可选）
        if (doc.getEmbeddingVector() != null) {
            resp.setEmbeddingVector(doc.getEmbeddingVector());
        }

        return resp;
    }
}
