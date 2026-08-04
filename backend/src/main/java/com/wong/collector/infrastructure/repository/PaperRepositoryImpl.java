package com.wong.collector.infrastructure.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wong.collector.domain.common.model.PageResult;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.repository.PaperQuery;
import com.wong.collector.domain.paper.repository.PaperRepository;
import com.wong.collector.infrastructure.persistence.converter.PaperConverter;
import com.wong.collector.infrastructure.persistence.mapper.DocumentChunkMapper;
import com.wong.collector.infrastructure.persistence.mapper.MaterialExtractionMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperDownloadMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperRelevantMapper;
import com.wong.collector.infrastructure.persistence.mapper.PaperTransformMapper;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.PaperDownloadPO;
import com.wong.collector.infrastructure.persistence.po.PaperPO;
import com.wong.collector.infrastructure.persistence.po.PaperRelevantPO;
import com.wong.collector.infrastructure.persistence.po.PaperTransformPO;

import lombok.RequiredArgsConstructor;

/**
 * PaperRepository 实现。
 */
@Repository
@RequiredArgsConstructor
public class PaperRepositoryImpl implements PaperRepository {

    private final PaperMapper paperMapper;
    private final PaperDownloadMapper downloadMapper;
    private final PaperTransformMapper transformMapper;
    private final PaperRelevantMapper relevantMapper;
    private final DocumentChunkMapper chunkMapper;
    private final MaterialExtractionMapper materialMapper;
    private final PaperConverter converter;

    @Override
    public Optional<Paper> findById(PaperId id) {
        PaperPO paperPO = paperMapper.selectById(id.getValue());
        if (paperPO == null) {
            return Optional.empty();
        }
        return Optional.of(mapToAggregate(id.getValue(), paperPO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Paper save(Paper paper) {
        PaperPO paperPO = converter.toPaperPO(paper);
        PaperPO existing = paperMapper.selectById(paperPO.getPaperId());
        if (existing == null) {
            paperMapper.insert(paperPO);
        } else {
            paperMapper.updateById(paperPO);
        }

        persistSubEntities(paper);
        return paper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(PaperId id) {
        paperMapper.deleteById(id.getValue());
        LambdaQueryWrapper<DocumentChunkPO> chunkWrapper = new LambdaQueryWrapper<DocumentChunkPO>()
            .eq(DocumentChunkPO::getPaperId, id.getValue());
        chunkMapper.delete(chunkWrapper);
        LambdaQueryWrapper<MaterialExtractionPO> materialWrapper = new LambdaQueryWrapper<MaterialExtractionPO>()
            .eq(MaterialExtractionPO::getPaperId, id.getValue());
        materialMapper.delete(materialWrapper);
        downloadMapper.delete(new LambdaQueryWrapper<PaperDownloadPO>().eq(PaperDownloadPO::getPaperId, id.getValue()));
        transformMapper.delete(new LambdaQueryWrapper<PaperTransformPO>().eq(PaperTransformPO::getPaperId, id.getValue()));
        relevantMapper.delete(new LambdaQueryWrapper<PaperRelevantPO>().eq(PaperRelevantPO::getPaperId, id.getValue()));
    }

    @Override
    public List<Paper> findByState(PaperState state, int limit) {
        int safeLimit = Math.max(limit, 1);
        LambdaQueryWrapper<PaperPO> wrapper = new LambdaQueryWrapper<PaperPO>()
            .eq(PaperPO::getStatus, state.name())
            .orderByDesc(PaperPO::getUpdateTime)
            .orderByDesc(PaperPO::getCreateTime)
            .orderByDesc(PaperPO::getPaperId)
            .last("limit " + safeLimit);
        List<PaperPO> records = paperMapper.selectList(wrapper);
        return records.stream()
            .map(this::mapToAggregateLite)
            .collect(Collectors.toList());
    }

    @Override
    public PageResult<Paper> page(PaperQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query is required");
        }

        LambdaQueryWrapper<PaperPO> countWrapper = buildQueryWrapper(query);
        long total = paperMapper.selectCount(countWrapper);
        if (total <= 0) {
            return PageResult.empty(query.page(), query.size());
        }
        long totalPages = PageResult.computeTotalPages(total, query.size());
        if (query.page() > totalPages) {
            return PageResult.of(List.of(), query.page(), query.size(), total);
        }

        long offset = query.offset();
        LambdaQueryWrapper<PaperPO> pageWrapper = buildQueryWrapper(query)
            .orderByDesc(PaperPO::getUpdateTime)
            .orderByDesc(PaperPO::getCreateTime)
            .orderByDesc(PaperPO::getPaperId)
            .last("limit " + query.size() + " offset " + offset);
        List<PaperPO> records = paperMapper.selectList(pageWrapper);
        List<Paper> items = records.stream()
            .map(this::mapToAggregateLite)
            .collect(Collectors.toList());
        return PageResult.of(items, query.page(), query.size(), total);
    }

    @Override
    public long countByState(PaperState state) {
        return paperMapper.selectCount(new LambdaQueryWrapper<PaperPO>()
            .eq(PaperPO::getStatus, state.name()));
    }

    @Override
    public boolean exists(PaperId id) {
        return paperMapper.selectById(id.getValue()) != null;
    }

    @Override
    public Optional<PaperId> findIdByDoi(String doi) {
        if (doi == null || doi.isBlank()) {
            return Optional.empty();
        }
        String paperId = paperMapper.findPaperIdByDoi(doi);
        if (paperId == null || paperId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PaperId.of(paperId));
    }

    private LambdaQueryWrapper<PaperPO> buildQueryWrapper(PaperQuery query) {
        LambdaQueryWrapper<PaperPO> wrapper = new LambdaQueryWrapper<>();
        if (query.state() != null) {
            wrapper.eq(PaperPO::getStatus, query.state().name());
        }
        return wrapper;
    }

    private Paper mapToAggregateLite(PaperPO paperPO) {
        Paper paper = converter.toAggregate(paperPO, null, null, null, List.of(), List.of());
        applyAuditFields(paper, paperPO);
        return paper;
    }

    /**
     * 将 PO 的审计字段映射到领域对象，避免列表/详情接口出现 createdAt 等字段为空。
     */
    private void applyAuditFields(Paper paper, PaperPO paperPO) {
        if (paper == null || paperPO == null) {
            return;
        }
        paper.setCreatedAt(paperPO.getCreateTime());
        paper.setUpdatedAt(paperPO.getUpdateTime());
        paper.setVersion(paperPO.getVersion());
    }

    private Paper mapToAggregate(String paperId, PaperPO paperPO) {
        PaperDownloadPO downloadPO = downloadMapper.selectOne(
            new LambdaQueryWrapper<PaperDownloadPO>().eq(PaperDownloadPO::getPaperId, paperId)
        );
        PaperTransformPO transformPO = transformMapper.selectOne(
            new LambdaQueryWrapper<PaperTransformPO>().eq(PaperTransformPO::getPaperId, paperId)
        );
        PaperRelevantPO relevantPO = relevantMapper.selectOne(
            new LambdaQueryWrapper<PaperRelevantPO>().eq(PaperRelevantPO::getPaperId, paperId)
        );
        List<DocumentChunkPO> chunkPOs = chunkMapper.selectList(
            new LambdaQueryWrapper<DocumentChunkPO>()
                .eq(DocumentChunkPO::getPaperId, paperId)
                .orderByAsc(DocumentChunkPO::getOrderNo)
        );
        List<MaterialExtractionPO> materialPOs = materialMapper.selectList(
            new LambdaQueryWrapper<MaterialExtractionPO>().eq(MaterialExtractionPO::getPaperId, paperId)
        );
        Paper paper = converter.toAggregate(paperPO, downloadPO, transformPO, relevantPO, chunkPOs, materialPOs);
        applyAuditFields(paper, paperPO);
        return paper;
    }

    private void persistSubEntities(Paper paper) {
        PaperDownloadPO downloadPO = converter.toDownloadPO(paper);
        if (downloadPO != null) {
            PaperDownloadPO existing = downloadMapper.selectOne(
                new LambdaQueryWrapper<PaperDownloadPO>().eq(PaperDownloadPO::getPaperId, downloadPO.getPaperId())
            );
            if (existing == null) {
                downloadMapper.insert(downloadPO);
            } else {
                downloadPO.setId(existing.getId());
                downloadMapper.updateById(downloadPO);
            }
        }

        PaperTransformPO transformPO = converter.toTransformPO(paper);
        if (transformPO != null) {
            PaperTransformPO existing = transformMapper.selectOne(
                new LambdaQueryWrapper<PaperTransformPO>().eq(PaperTransformPO::getPaperId, transformPO.getPaperId())
            );
            if (existing == null) {
                transformMapper.insert(transformPO);
            } else {
                transformPO.setId(existing.getId());
                transformMapper.updateById(transformPO);
            }
        }

        PaperRelevantPO relevantPO = converter.toRelevantPO(paper);
        if (relevantPO != null) {
            PaperRelevantPO existing = relevantMapper.selectOne(
                new LambdaQueryWrapper<PaperRelevantPO>().eq(PaperRelevantPO::getPaperId, relevantPO.getPaperId())
            );
            if (existing == null) {
                relevantMapper.insert(relevantPO);
            } else {
                relevantPO.setId(existing.getId());
                relevantMapper.updateById(relevantPO);
            }
        }

        // 不采用 delete+insert：否则每次保存都会重建 BIGSERIAL id，导致引用失效。
        List<DocumentChunkPO> chunkPOs = converter.toChunkPOs(paper);
        if (chunkPOs.isEmpty()) {
            chunkMapper.delete(new LambdaQueryWrapper<DocumentChunkPO>()
                .eq(DocumentChunkPO::getPaperId, paper.getId().getValue()));
        } else {
            List<Integer> orderNos = chunkPOs.stream()
                .map(DocumentChunkPO::getOrderNo)
                .toList();
            // 先删除已不存在的 chunk（按 order_no），避免旧数据残留。
            chunkMapper.delete(new LambdaQueryWrapper<DocumentChunkPO>()
                .eq(DocumentChunkPO::getPaperId, paper.getId().getValue())
                .notIn(DocumentChunkPO::getOrderNo, orderNos));
            // 再按 (paper_id, order_no) upsert，确保同一逻辑 chunk 的 id 稳定不变。
            for (DocumentChunkPO chunkPO : chunkPOs) {
                chunkMapper.upsertByPaperAndOrder(chunkPO);
            }
        }

        materialMapper.delete(new LambdaQueryWrapper<MaterialExtractionPO>().eq(MaterialExtractionPO::getPaperId, paper.getId().getValue()));
        for (MaterialExtractionPO materialPO : converter.toMaterialPOs(paper)) {
            materialMapper.insert(materialPO);
        }
    }
}
