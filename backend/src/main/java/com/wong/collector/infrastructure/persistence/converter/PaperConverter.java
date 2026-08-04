package com.wong.collector.infrastructure.persistence.converter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.paper.model.Authors;
import com.wong.collector.domain.paper.model.DOI;
import com.wong.collector.domain.paper.model.DocumentChunk;
import com.wong.collector.domain.paper.model.DownloadInfo;
import com.wong.collector.domain.paper.model.DownloadStatus;
import com.wong.collector.domain.paper.model.PaperEmbeddingModel;
import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperSnapshot;
import com.wong.collector.domain.paper.model.PaperSource;
import com.wong.collector.domain.paper.model.PaperState;
import com.wong.collector.domain.paper.model.RelevanceAnalyzer;
import com.wong.collector.domain.paper.model.RelevanceInfo;
import com.wong.collector.domain.paper.model.Title;
import com.wong.collector.domain.paper.model.TransformInfo;
import com.wong.collector.domain.paper.model.TransformProvider;
import com.wong.collector.domain.paper.model.TransformStatus;
import com.wong.collector.domain.paper.model.YearPublished;
import com.wong.collector.infrastructure.persistence.po.DocumentChunkPO;
import com.wong.collector.infrastructure.persistence.po.MaterialExtractionPO;
import com.wong.collector.infrastructure.persistence.po.PaperDownloadPO;
import com.wong.collector.infrastructure.persistence.po.PaperPO;
import com.wong.collector.infrastructure.persistence.po.PaperRelevantPO;
import com.wong.collector.infrastructure.persistence.po.PaperTransformPO;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

/**
 * Paper 聚合与持久化对象之间的转换器。
 */
@Component
public class PaperConverter {

    public PaperPO toPaperPO(Paper paper) {
        PaperSnapshot snapshot = paper.snapshot();
        PaperPO po = new PaperPO();
        po.setPaperId(snapshot.id().getValue());
        po.setTitle(snapshot.title().value());
        po.setAuthors(snapshot.authors().asString());
        po.setDoi(snapshot.doi() == null ? null : snapshot.doi().value());
        po.setYearPublished(snapshot.yearPublished() == null ? null : snapshot.yearPublished().value());
        po.setPaperSource(snapshot.source() == null ? null : snapshot.source().name());
        po.setAbstractContent(snapshot.abstractContent());
        po.setDownloadUrl(snapshot.downloadUrl());
        po.setCitationCount(snapshot.citationCount());
        po.setStatus(snapshot.state().name());
        return po;
    }

    public Paper toAggregate(PaperPO paperPO,
                              PaperDownloadPO downloadPO,
                              PaperTransformPO transformPO,
                              PaperRelevantPO relevantPO,
                              List<DocumentChunkPO> chunkPOs,
                              List<MaterialExtractionPO> materialPOs) {
        List<String> authorList = StrUtil.isBlank(paperPO.getAuthors())
            ? List.of("Unknown")
            : StrUtil.splitTrim(paperPO.getAuthors(), ',');
        PaperSnapshot snapshot = new PaperSnapshot(
            PaperId.of(paperPO.getPaperId()),
            new Title(paperPO.getTitle()),
            Authors.of(authorList),
            paperPO.getDoi() == null ? null : new DOI(paperPO.getDoi()),
            paperPO.getYearPublished() == null ? null : new YearPublished(paperPO.getYearPublished()),
            paperPO.getPaperSource() == null ? null : PaperSource.valueOf(paperPO.getPaperSource()),
            paperPO.getAbstractContent(),
            paperPO.getDownloadUrl(),
            paperPO.getCitationCount(),
            toDownloadInfo(downloadPO),
            toTransformInfo(transformPO),
            toRelevanceInfo(relevantPO),
            toChunks(chunkPOs),
            toMaterials(materialPOs),
            PaperState.valueOf(paperPO.getStatus())
        );
        return Paper.restore(snapshot);
    }

    public PaperDownloadPO toDownloadPO(Paper paper) {
        DownloadInfo info = paper.getDownloadInfo();
        if (info == null) {
            return null;
        }
        PaperDownloadPO po = new PaperDownloadPO();
        po.setPaperId(paper.getId().getValue());
        po.setSourceUrl(info.getSourceUrl());
        po.setOssUrl(info.getOssUrl());
        po.setFileSize(info.getFileSize());
        po.setTryTimes(info.getTryTimes());
        po.setDownloadStatus(info.getStatus().name());
        po.setErrorMessage(info.getErrorMessage());
        po.setStartTime(info.getStartedAt());
        po.setFinishTime(info.getCompletedAt());
        return po;
    }

    public PaperTransformPO toTransformPO(Paper paper) {
        TransformInfo info = paper.getTransformInfo();
        if (info == null) {
            return null;
        }
        PaperTransformPO po = new PaperTransformPO();
        po.setPaperId(paper.getId().getValue());
        po.setTransformProvider(info.getProvider().name());
        po.setStatus(info.getStatus().name());
        po.setMarkdownPath(info.getMarkdownPath());
        po.setErrorMessage(info.getErrorMessage());
        po.setStartTime(info.getStartedAt());
        po.setFinishTime(info.getCompletedAt());
        return po;
    }

    public PaperRelevantPO toRelevantPO(Paper paper) {
        RelevanceInfo info = paper.getRelevanceInfo();
        if (info == null) {
            return null;
        }
        PaperRelevantPO po = new PaperRelevantPO();
        po.setPaperId(paper.getId().getValue());
        po.setRelevant(info.isRelevant());
        po.setScore(info.getScore());
        po.setAnalyzer(info.getAnalyzer().name());
        po.setReason(info.getReason());
        po.setRawResult(StrUtil.isBlank(info.getRawResult()) ? null : info.getRawResult());
        po.setKeywords(StrUtil.isBlank(info.getKeywordsSnapshot()) ? null : info.getKeywordsSnapshot());
        po.setThresholdUsed(info.getThresholdUsed());
        po.setHasExtractableMaterialInfo(info.getHasExtractableMaterialInfo());
        return po;
    }

    public List<DocumentChunkPO> toChunkPOs(Paper paper) {
        if (CollUtil.isEmpty(paper.getChunks())) {
            return List.of();
        }
        return paper.getChunks().stream()
            .map(chunk -> {
                DocumentChunkPO po = new DocumentChunkPO();
                po.setPaperId(paper.getId().getValue());
                po.setOrderNo(chunk.getOrderNo());
                po.setContent(chunk.getContent());
                po.setEmbeddingModel(chunk.getModel() == null ? null : chunk.getModel().name());
                po.setVector(chunk.getVector());
                return po;
            }).collect(Collectors.toList());
    }

    public List<MaterialExtractionPO> toMaterialPOs(Paper paper) {
        if (CollUtil.isEmpty(paper.getMaterials())) {
            return List.of();
        }
        return paper.getMaterials().stream().map(material -> {
            MaterialExtractionPO po = new MaterialExtractionPO();
            po.setPaperId(paper.getId().getValue());
            po.setMaterial(material.getMaterial());
            po.setProperty(material.getProperty());
            po.setMethod(material.getMethod());
            po.setConfidence(material.getConfidence());
            po.setDetailJson(StrUtil.isBlank(material.getDetailJson()) ? null : material.getDetailJson());
            return po;
        }).collect(Collectors.toList());
    }

    private DownloadInfo toDownloadInfo(PaperDownloadPO po) {
        if (po == null) {
            return null;
        }
        DownloadStatus status = po.getDownloadStatus() == null
            ? DownloadStatus.PENDING
            : DownloadStatus.valueOf(po.getDownloadStatus());
        return DownloadInfo.restore(
            po.getSourceUrl(),
            status,
            po.getOssUrl(),
            po.getFileSize(),
            Optional.ofNullable(po.getTryTimes()).orElse(0),
            po.getErrorMessage(),
            po.getStartTime(),
            po.getFinishTime()
        );
    }

    private TransformInfo toTransformInfo(PaperTransformPO po) {
        if (po == null || po.getTransformProvider() == null) {
            return null;
        }
        TransformStatus status = po.getStatus() == null
            ? TransformStatus.PENDING
            : TransformStatus.valueOf(po.getStatus());
        return TransformInfo.restore(
            TransformProvider.valueOf(po.getTransformProvider()),
            status,
            po.getMarkdownPath(),
            po.getErrorMessage(),
            po.getStartTime(),
            po.getFinishTime()
        );
    }

    private RelevanceInfo toRelevanceInfo(PaperRelevantPO po) {
        if (po == null) {
            return null;
        }
        RelevanceAnalyzer analyzer = po.getAnalyzer() == null
            ? RelevanceAnalyzer.LOCAL_MODEL
            : RelevanceAnalyzer.valueOf(po.getAnalyzer());
        return RelevanceInfo.of(po.getRelevant() != null && po.getRelevant(),
            Optional.ofNullable(po.getScore()).orElse(0d),
            analyzer,
            po.getReason(),
            po.getRawResult(),
            po.getKeywords(),
            po.getThresholdUsed(),
            po.getHasExtractableMaterialInfo());
    }

    private List<DocumentChunk> toChunks(List<DocumentChunkPO> pos) {
        if (CollUtil.isEmpty(pos)) {
            return Collections.emptyList();
        }
        return pos.stream().map(po -> DocumentChunk.restore(
            po.getId(),
            po.getOrderNo(),
            po.getContent(),
            po.getEmbeddingModel() == null ? null : PaperEmbeddingModel.valueOf(po.getEmbeddingModel()),
            po.getVector()
        )).collect(Collectors.toList());
    }

    private List<MaterialExtraction> toMaterials(List<MaterialExtractionPO> pos) {
        if (CollUtil.isEmpty(pos)) {
            return Collections.emptyList();
        }
        return pos.stream()
            .map(po -> MaterialExtraction.of(po.getMaterial(), po.getProperty(), po.getMethod(),
                Optional.ofNullable(po.getConfidence()).orElse(0d),
                po.getDetailJson()))
            .collect(Collectors.toList());
    }
}
