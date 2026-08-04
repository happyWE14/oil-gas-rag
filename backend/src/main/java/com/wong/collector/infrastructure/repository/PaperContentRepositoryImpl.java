package com.wong.collector.infrastructure.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wong.collector.domain.paper.model.PaperContent;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.TextSourceType;
import com.wong.collector.domain.paper.repository.PaperContentRepository;
import com.wong.collector.infrastructure.persistence.mapper.PaperContentMapper;
import com.wong.collector.infrastructure.persistence.po.PaperContentPO;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaperContentRepositoryImpl implements PaperContentRepository {

    private final PaperContentMapper mapper;

    @Override
    public PaperContent saveNewVersion(PaperContent content) {
        Integer maxVersion = mapper.findMaxVersion(content.getPaperId().getValue());
        int nextVersion = maxVersion == null ? 1 : maxVersion + 1;
        PaperContentPO po = toPO(content);
        po.setVersion(nextVersion);
        po.setCreatedAt(LocalDateTime.now());
        mapper.insert(po);
        return PaperContent.restore(
            PaperId.of(po.getPaperId()),
            nextVersion,
            content.getSourceType(),
            content.getCharset(),
            content.getChecksum(),
            content.getSize(),
            content.getContent(),
            po.getCreatedAt()
        );
    }

    @Override
    public Optional<PaperContent> findLatest(PaperId paperId) {
        PaperContentPO po = mapper.selectOne(new LambdaQueryWrapper<PaperContentPO>()
            .eq(PaperContentPO::getPaperId, paperId.getValue())
            .orderByDesc(PaperContentPO::getVersion)
            .last("limit 1"));
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    private PaperContentPO toPO(PaperContent content) {
        PaperContentPO po = new PaperContentPO();
        po.setPaperId(content.getPaperId().getValue());
        po.setSourceType(content.getSourceType() == null ? null : content.getSourceType().name());
        po.setCharset(content.getCharset());
        po.setChecksum(content.getChecksum());
        po.setSize(content.getSize());
        po.setContent(content.getContent());
        return po;
    }

    private PaperContent toDomain(PaperContentPO po) {
        return PaperContent.restore(
            PaperId.of(po.getPaperId()),
            po.getVersion() == null ? 1 : po.getVersion(),
            po.getSourceType() == null ? TextSourceType.OTHER : TextSourceType.valueOf(po.getSourceType()),
            po.getCharset(),
            po.getChecksum(),
            po.getSize() == null ? 0L : po.getSize(),
            po.getContent(),
            po.getCreatedAt()
        );
    }
}
