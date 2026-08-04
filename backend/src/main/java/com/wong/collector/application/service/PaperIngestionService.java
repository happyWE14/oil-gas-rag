package com.wong.collector.application.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.wong.collector.domain.common.event.EventPublisher;
import com.wong.collector.domain.paper.model.DOI;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.repository.PaperRepository;

import lombok.RequiredArgsConstructor;

/**
 * 统一的论文入库入口：跨来源按 DOI 去重，并按策略决定是否触发后续流水线。
 */
@Service
@RequiredArgsConstructor
public class PaperIngestionService {

    private final PaperRepository paperRepository;
    private final EventPublisher eventPublisher;

    public record IngestionResult(PaperId paperId, boolean createdNew, boolean enrichedExisting) {
        public static IngestionResult created(PaperId paperId) {
            return new IngestionResult(paperId, true, false);
        }

        public static IngestionResult existing(PaperId paperId, boolean enrichedExisting) {
            return new IngestionResult(paperId, false, enrichedExisting);
        }
    }

    /**
     * 入库或归并：\n
     * - DOI 命中已有论文：仅补齐缺失元数据（可选），不发布 PaperDiscoveredEvent，不触发流水线。\n
     * - 新论文：保存并发布 PaperDiscoveredEvent，让既有流程接管。\n
     * - 并发/重复写入：依赖 DB 约束（paper_id PK + DOI 唯一索引）做强一致去重。\n
     */
    public IngestionResult ingest(Paper candidate) {
        return ingest(candidate, true);
    }

    public IngestionResult ingest(Paper candidate, boolean enrichExisting) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate paper is null");
        }
        PaperId resolvedId = resolveExistingByDoi(candidate.getDoi()).orElse(null);
        if (resolvedId != null) {
            boolean enriched = enrichExisting && tryEnrichExisting(resolvedId, candidate);
            return IngestionResult.existing(resolvedId, enriched);
        }
        if (paperRepository.exists(candidate.getId())) {
            boolean enriched = enrichExisting && tryEnrichExisting(candidate.getId(), candidate);
            return IngestionResult.existing(candidate.getId(), enriched);
        }

        try {
            paperRepository.save(candidate);
            publishDomainEvents(candidate);
            return IngestionResult.created(candidate.getId());
        } catch (DataIntegrityViolationException dup) {
            PaperId id = resolveExistingByDoi(candidate.getDoi()).orElse(null);
            if (id == null && paperRepository.exists(candidate.getId())) {
                id = candidate.getId();
            }
            if (id == null) {
                throw dup;
            }
            boolean enriched = enrichExisting && tryEnrichExisting(id, candidate);
            return IngestionResult.existing(id, enriched);
        }
    }

    private Optional<PaperId> resolveExistingByDoi(DOI doi) {
        if (doi == null) {
            return Optional.empty();
        }
        return paperRepository.findIdByDoi(doi.value());
    }

    private boolean tryEnrichExisting(PaperId paperId, Paper candidate) {
        Paper existing = paperRepository.findById(paperId).orElse(null);
        if (existing == null) {
            return false;
        }
        boolean changed = existing.enrichIfMissing(candidate);
        if (changed) {
            paperRepository.save(existing);
        }
        return changed;
    }

    private void publishDomainEvents(Paper paper) {
        paper.getDomainEvents().forEach(eventPublisher::publish);
        paper.clearDomainEvents();
    }
}
