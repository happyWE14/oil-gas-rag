package com.wong.collector.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wong.collector.application.assembler.PaperAssembler;
import com.wong.collector.application.dto.request.CreatePaperRequest;
import com.wong.collector.application.dto.request.DownloadCompletedRequest;
import com.wong.collector.application.dto.request.EmbeddingCompletedRequest;
import com.wong.collector.application.dto.request.MaterialExtractionRequest;
import com.wong.collector.application.dto.request.PreAnalysisResultRequest;
import com.wong.collector.application.dto.request.TransformCompletedRequest;
import com.wong.collector.application.dto.response.PaperDTO;
import com.wong.collector.application.dto.response.PaperDetailDTO;
import com.wong.collector.application.dto.response.PaperStateEventDTO;
import com.wong.collector.application.service.search.MaterialSearchApplicationService;
import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.common.exception.NotFoundException;
import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.paper.model.Authors;
import com.wong.collector.domain.paper.model.DOI;
import com.wong.collector.domain.paper.model.DocumentChunk;
import com.wong.collector.domain.paper.model.PaperEmbeddingModel;
import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperSource;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.RelevanceAnalyzer;
import com.wong.collector.domain.paper.model.Title;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.domain.paper.model.YearPublished;
import com.wong.collector.domain.paper.repository.PaperQuery;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.domain.paper.repository.PaperStateEventRepository;
import com.wong.collector.infrastructure.persistence.mapper.MaterialMetricMapper;
import com.wong.collector.infrastructure.persistence.po.MaterialMetricPO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperApplicationService {

    private final PaperRepository paperRepository;
    private final PaperAssembler assembler;
    private final EventPublisher eventPublisher;
    private final PaperStateEventRepository paperStateEventRepository;
    private final MaterialMetricMapper materialMetricMapper;

    // 新增：用于同步到 ES 和 material_metric
    private final MaterialSearchApplicationService materialSearchApplicationService;

    @Transactional(rollbackFor = Exception.class)
    public PaperDetailDTO createPaper(CreatePaperRequest request) {
        Paper paper = Paper.create(
                PaperId.newId(),
                new Title(request.getTitle()),
                Authors.of(request.getAuthors()),
                request.getDoi() == null ? null : new DOI(request.getDoi()),
                request.getYearPublished() == null ? null : new YearPublished(request.getYearPublished()),
                PaperSource.valueOf(request.getPaperSource()),
                request.getAbstractContent(),
                request.getDownloadUrl(),
                request.getCitationCount()
        );
        paperRepository.save(paper);
        publishDomainEvents(paper);
        return assembler.toDetailDTO(paper);
    }

    /**
     * 开始预分析（供任务处理器内部调用）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void startPreAnalysis(String paperId) {
        Paper paper = loadPaper(paperId);
        if (paper.getState() == PaperState.PRE_ANALYZING) {
            return;
        }
        if (paper.getState() != PaperState.DISCOVERED) {
            return;
        }
        paper.startPreAnalysis();
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failPreAnalysis(String paperId, String errorMessage) {
        Paper paper = loadPaper(paperId);
        paper.failPreAnalysis(errorMessage == null ? "UNKNOWN_ERROR" : errorMessage);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completePreAnalysis(String paperId, PreAnalysisResultRequest request) {
        Paper paper = loadPaper(paperId);
        RelevanceAnalyzer analyzer = parseAnalyzerSafely(request.getAnalyzer());
        paper.completePreAnalysis(
                request.getRelevant(),
                request.getScore() == null ? 0d : request.getScore(),
                request.getReason(),
                analyzer,
                request.getRawResult(),
                request.getKeywordsSnapshot(),
                request.getThresholdUsed(),
                request.getHasExtractableMaterialInfo()
        );
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startDownload(String paperId) {
        Paper paper = loadPaper(paperId);
        if (paper.getState() == PaperState.DOWNLOADING) {
            return;
        }
        if (paper.getState() != PaperState.PRE_RELEVANT) {
            return;
        }
        paper.startDownload();
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeDownload(String paperId, DownloadCompletedRequest request) {
        Paper paper = loadPaper(paperId);
        paper.completeDownload(request.getOssUrl(), request.getFileSize());
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failDownload(String paperId, String errorMessage) {
        Paper paper = loadPaper(paperId);
        paper.failDownload(errorMessage == null ? "UNKNOWN_ERROR" : errorMessage);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startTransform(String paperId, TransformProvider provider) {
        Paper paper = loadPaper(paperId);
        if (paper.getState() == PaperState.TRANSFORMING) {
            return;
        }
        if (paper.getState() != PaperState.DOWNLOADED) {
            return;
        }
        paper.startTransform(provider);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failTransform(String paperId, String errorMessage) {
        Paper paper = loadPaper(paperId);
        paper.failTransform(errorMessage == null ? "UNKNOWN_ERROR" : errorMessage);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeTransform(String paperId, TransformCompletedRequest request) {
        Paper paper = loadPaper(paperId);
        paper.completeTransform(request.getMarkdownPath());
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startEmbedding(String paperId) {
        Paper paper = loadPaper(paperId);
        if (paper.getState() == PaperState.EMBEDDING) {
            return;
        }
        if (paper.getState() != PaperState.TRANSFORMED) {
            return;
        }
        paper.startEmbedding();
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeEmbedding(String paperId, EmbeddingCompletedRequest request) {
        Paper paper = loadPaper(paperId);
        request.getChunks().forEach(chunk -> paper.addChunk(
                DocumentChunk.of(
                        chunk.getOrderNo(),
                        chunk.getContent(),
                        chunk.getEmbeddingModel() == null ? null : PaperEmbeddingModel.valueOf(chunk.getEmbeddingModel()),
                        chunk.getVector()
                )));
        paper.completeEmbedding();
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failEmbedding(String paperId, String errorMessage) {
        Paper paper = loadPaper(paperId);
        paper.failEmbedding(errorMessage == null ? "UNKNOWN_ERROR" : errorMessage);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startExtraction(String paperId) {
        Paper paper = loadPaper(paperId);
        if (paper.getState() == PaperState.EXTRACTING) {
            return;
        }
        if (paper.getState() != PaperState.EMBEDDING_COMPLETED) {
            return;
        }
        paper.startExtraction();
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void failExtraction(String paperId, String errorMessage) {
        Paper paper = loadPaper(paperId);
        paper.failExtraction(errorMessage == null ? "UNKNOWN_ERROR" : errorMessage);
        paperRepository.save(paper);
        publishDomainEvents(paper);
    }

    /**
     * 完成材料提取
     *
     * 关键改造：
     * 1. 同步操作在事务提交后执行（使用 TransactionSynchronization），避免影响主业务
     * 2. 同步失败不影响论文状态变更
     * 3. 处理 property 为 null 的情况
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeExtraction(String paperId, MaterialExtractionRequest request) {
        Paper paper = loadPaper(paperId);
        paper.completeExtraction(convertMaterials(request));
        paper.complete();
        paperRepository.save(paper);

        // 发布领域事件
        publishDomainEvents(paper);

        // 在事务提交后执行同步，确保主事务成功提交后才进行后续操作
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务提交后执行同步，使用独立事务确保隔离性
                    syncToExternalSystems(paperId, request);
                }
            });
        } else {
            // 无事务环境，直接执行
            syncToExternalSystems(paperId, request);
        }
    }

    /**
     * 同步到外部系统（ES 和 material_metric）
     * 使用 REQUIRES_NEW 确保每个操作独立事务，互不影响
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncToExternalSystems(String paperId, MaterialExtractionRequest request) {
        // 1. 同步到 material_metric 表（独立事务）
        try {
            doSyncMaterialMetrics(paperId, request);
            log.info("✅ Material metric 同步成功, paperId={}", paperId);
        } catch (Exception e) {
            log.error("❌ Material metric 同步失败, paperId={}, error={}", paperId, e.getMessage(), e);
        }

        // 2. 同步到 Elasticsearch（独立事务）
        try {
            materialSearchApplicationService.syncByPaperId(paperId);
            log.info("✅ ES 同步成功, paperId={}", paperId);
        } catch (Exception e) {
            log.error("❌ ES 同步失败, paperId={}, error={}", paperId, e.getMessage(), e);
            if (e.getMessage() != null && e.getMessage().contains("mapping set to strict")) {
                log.error("提示：请检查 ES Mapping 是否包含所有字段");
            }
        }
    }

    /**
     * 实际执行 material_metric 同步（独立事务）
     * 处理 property 为 null 的情况
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void doSyncMaterialMetrics(String paperId, MaterialExtractionRequest request) {
        if (request == null || request.getMaterials() == null || request.getMaterials().isEmpty()) {
            log.warn("材料提取结果为空，清理 material_metric 历史数据, paperId={}", paperId);
            materialMetricMapper.delete(new QueryWrapper<MaterialMetricPO>().eq("paper_id", paperId));
            return;
        }

        // 清理历史数据
        int deleted = materialMetricMapper.delete(new QueryWrapper<MaterialMetricPO>().eq("paper_id", paperId));
        log.debug("清理旧材料数据: paperId={}, deleted={}", paperId, deleted);

        LocalDateTime now = LocalDateTime.now();
        int successCount = 0;
        int skipCount = 0;

        for (MaterialExtractionRequest.MaterialDTO material : request.getMaterials()) {
            // 关键修复：跳过 property 为 null 或空的数据
            if (material.getProperty() == null || material.getProperty().trim().isEmpty()) {
                log.warn("跳过 property 为空的材料记录, paperId={}, material={}", paperId, material.getMaterial());
                skipCount++;
                continue;
            }

            MaterialMetricPO po = new MaterialMetricPO();
            po.setPaperId(paperId);
            po.setMaterialKey(material.getMaterial());
            po.setMetricKey(material.getProperty());  // 现在确保不为 null
            po.setQualifier(material.getMethod());
            po.setConfidence(material.getConfidence() == null ? 0d : material.getConfidence());

            // 将 detailJson 存入 valueText（String 类型，避免 JSONB 问题）
            if (material.getDetailJson() != null && !material.getDetailJson().trim().isEmpty()) {
                po.setValueText(material.getDetailJson());
            }

            po.setCreateTime(now);
            po.setUpdateTime(now);

            materialMetricMapper.insert(po);
            successCount++;
        }

        log.info("已同步 {} 条材料记录到 material_metric (跳过 {} 条), paperId={}",
                successCount, skipCount, paperId);
    }

    @Transactional(readOnly = true)
    public PaperDetailDTO getPaperDetail(String paperId, boolean includeChunks) {
        Paper paper = loadPaper(paperId);
        return assembler.toDetailDTO(paper, includeChunks);
    }

    @Transactional(readOnly = true)
    public PaperDetailDTO getPaperDetailByDoi(String doi, boolean includeChunks) {
        DOI doiValue = new DOI(normalizeDoi(doi));
        PaperId paperId = paperRepository.findIdByDoi(doiValue.value())
                .orElseThrow(() -> new NotFoundException("Paper not found for DOI: " + doiValue.value()));
        return getPaperDetail(paperId.getValue(), includeChunks);
    }

    @Transactional(readOnly = true)
    public List<PaperDTO> listByState(PaperState state, int limit) {
        return paperRepository.findByState(state, limit).stream()
                .map(assembler::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 分页查询论文列表（预留多条件过滤扩展点）。
     */
    @Transactional(readOnly = true)
    public PageResult<PaperDTO> pagePapers(PaperQuery query) {
        return paperRepository.page(query).map(assembler::toDTO);
    }

    @Transactional(readOnly = true)
    public List<PaperStateEventDTO> listStateEvents(String paperId, int limit) {
        return paperStateEventRepository.findRecentEvents(PaperId.of(paperId), limit).stream()
                .map(event -> PaperStateEventDTO.builder()
                        .fromState(event.getFromState().name())
                        .toState(event.getToState().name())
                        .operator(event.getOperator())
                        .reason(event.getReason())
                        .occurredAt(event.getOccurredAt())
                        .build())
                .collect(Collectors.toList());
    }

    private Paper loadPaper(String paperId) {
        return paperRepository.findById(PaperId.of(paperId))
                .orElseThrow(() -> new NotFoundException("Paper not found: " + paperId));
    }

    private List<MaterialExtraction> convertMaterials(MaterialExtractionRequest request) {
        if (request == null || request.getMaterials() == null) {
            return List.of();
        }
        return request.getMaterials().stream()
                .map(material -> MaterialExtraction.of(
                        material.getMaterial(),
                        material.getProperty(),
                        material.getMethod(),
                        material.getConfidence() == null ? 0d : material.getConfidence(),
                        material.getDetailJson()
                )).collect(Collectors.toList());
    }

    private RelevanceAnalyzer parseAnalyzerSafely(String analyzerText) {
        if (analyzerText == null || analyzerText.isBlank()) {
            return RelevanceAnalyzer.LOCAL_MODEL;
        }
        try {
            return RelevanceAnalyzer.valueOf(analyzerText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return RelevanceAnalyzer.LOCAL_MODEL;
        }
    }

    private String normalizeDoi(String doi) {
        if (doi == null) {
            return null;
        }
        String trimmed = doi.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("doi:")) {
            return trimmed.substring(4).trim();
        }
        if (lower.startsWith("https://doi.org/")) {
            return trimmed.substring("https://doi.org/".length()).trim();
        }
        if (lower.startsWith("http://doi.org/")) {
            return trimmed.substring("http://doi.org/".length()).trim();
        }
        return trimmed;
    }

    private void publishDomainEvents(Paper paper) {
        paper.getDomainEvents().forEach(eventPublisher::publish);
        paper.clearDomainEvents();
    }
}
