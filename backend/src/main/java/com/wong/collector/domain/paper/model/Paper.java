package com.wong.collector.domain.paper.model;

import com.wong.collector.domain.common.aggregate.AggregateRoot;
import com.wong.collector.domain.common.exception.DomainException;
import com.wong.collector.domain.common.exception.IllegalStateTransitionException;
import com.wong.collector.domain.paper.event.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 论文聚合根。
 * 职责：论文业务生命周期管理
 * 重试机制由 TaskRecord 负责，Paper 不参与重试逻辑
 */
@Getter
public class Paper extends AggregateRoot<PaperId> {

    private Title title;
    private Authors authors;
    private DOI doi;
    private YearPublished yearPublished;
    private PaperSource paperSource;
    private String abstractContent;
    private String downloadUrl;
    private Integer citationCount;

    private DownloadInfo downloadInfo;
    private TransformInfo transformInfo;
    private RelevanceInfo relevanceInfo;
    private List<DocumentChunk> chunks;
    private List<MaterialExtraction> materials;

    private PaperState state;

    private Paper() {
        this.chunks = new ArrayList<>();
        this.materials = new ArrayList<>();
    }

    public static Paper create(PaperId id, Title title, Authors authors,
                               DOI doi, YearPublished yearPublished,
                               PaperSource source, String abstractContent,
                               String downloadUrl, Integer citationCount) {
        Objects.requireNonNull(id, "Paper id required");
        Objects.requireNonNull(title, "Paper title required");
        Objects.requireNonNull(authors, "Paper authors required");
        Paper paper = new Paper();
        paper.setId(id);
        paper.title = title;
        paper.authors = authors;
        paper.doi = doi;
        paper.yearPublished = yearPublished;
        paper.paperSource = source;
        paper.abstractContent = abstractContent;
        paper.downloadUrl = downloadUrl;
        paper.citationCount = citationCount;
        paper.state = PaperState.DISCOVERED;
        paper.registerEvent(new PaperDiscoveredEvent(paper));
        return paper;
    }

    public boolean enrichIfMissing(Paper candidate) {
        if (candidate == null) {
            return false;
        }
        boolean changed = false;
        if (this.doi == null && candidate.doi != null) {
            this.doi = candidate.doi;
            changed = true;
        }
        if ((this.abstractContent == null || this.abstractContent.isBlank())
            && candidate.abstractContent != null && !candidate.abstractContent.isBlank()) {
            this.abstractContent = candidate.abstractContent;
            changed = true;
        }
        if ((this.downloadUrl == null || this.downloadUrl.isBlank())
            && candidate.downloadUrl != null && !candidate.downloadUrl.isBlank()) {
            this.downloadUrl = candidate.downloadUrl;
            changed = true;
        }
        if (this.citationCount == null && candidate.citationCount != null) {
            this.citationCount = candidate.citationCount;
            changed = true;
        }
        if (this.yearPublished == null && candidate.yearPublished != null) {
            this.yearPublished = candidate.yearPublished;
            changed = true;
        }
        return changed;
    }

    // ========== 预分析阶段 ==========

    public void startPreAnalysis() {
        if (this.state == PaperState.PRE_ANALYZING) {
            return;
        }
        if (this.state != PaperState.DISCOVERED) {
            return;
        }
        this.state = PaperState.PRE_ANALYZING;
        this.registerEvent(new PaperPreAnalysisStartedEvent(this));
    }

    public void completePreAnalysis(boolean isRelevant, double score,
                                    String reason, RelevanceAnalyzer analyzer,
                                    String rawResult, String keywordsSnapshot,
                                    Double thresholdUsed, Boolean hasExtractableMaterialInfo) {
        if (this.state != PaperState.PRE_ANALYZING) {
            if (this.relevanceInfo != null
                && (this.state == PaperState.PRE_RELEVANT
                || this.state == PaperState.IRRELEVANT
                || this.state == PaperState.COMPLETED)) {
                return;
            }
            return;
        }
        this.relevanceInfo = RelevanceInfo.of(isRelevant, score, analyzer, reason,
            rawResult, keywordsSnapshot, thresholdUsed, hasExtractableMaterialInfo);
        if (isRelevant) {
            transitionTo(PaperState.PRE_RELEVANT);
            this.registerEvent(new PaperPreRelevantEvent(this, score, reason));
        } else {
            transitionTo(PaperState.IRRELEVANT);
            this.registerEvent(new PreAnalysisIrrelevantEvent(this, score, reason));
        }
    }

    public void failPreAnalysis(String message) {
        if (this.state != PaperState.PRE_ANALYZING) {
            return;
        }
        transitionTo(PaperState.FAILED);
        this.registerEvent(new PreAnalysisFailedEvent(this, message));
    }

    // ========== 下载阶段 ==========

    public void startDownload() {
        if (this.state != PaperState.PRE_RELEVANT) {
            return;
        }
        if (this.downloadInfo == null) {
            this.downloadInfo = DownloadInfo.create(downloadUrl);
        }
        this.downloadInfo.startDownload();
        transitionTo(PaperState.DOWNLOADING);
        this.registerEvent(new PaperDownloadStartedEvent(this));
    }

    public void completeDownload(String ossUrl, Long fileSize) {
        if (this.state != PaperState.DOWNLOADING) {
            if (this.downloadInfo != null && this.downloadInfo.isCompleted()) {
                return;
            }
            return;
        }
        this.downloadInfo.complete(ossUrl, fileSize);
        transitionTo(PaperState.DOWNLOADED);
        this.registerEvent(new PaperDownloadedEvent(this, ossUrl, fileSize));
    }

    public void failDownload(String message) {
        if (downloadInfo != null) {
            downloadInfo.fail(message);
        }
        transitionTo(PaperState.FAILED);
        this.registerEvent(new PaperDownloadFailedEvent(this, message));
    }

    // ========== 转换阶段 ==========

    public void startTransform(TransformProvider provider) {
        if (this.state != PaperState.DOWNLOADED) {
            return;
        }
        this.transformInfo = TransformInfo.init(provider);
        this.transformInfo.start(null);
        transitionTo(PaperState.TRANSFORMING);
        this.registerEvent(new PaperTransformStartedEvent(this, provider));
    }

    public void completeTransform(String markdownPath) {
        if (this.state != PaperState.TRANSFORMING) {
            if (this.transformInfo != null && this.transformInfo.isCompleted()) {
                return;
            }
            return;
        }
        this.transformInfo.complete(markdownPath);
        transitionTo(PaperState.TRANSFORMED);
        this.registerEvent(new PaperTransformedEvent(this, markdownPath));
    }

    public void failTransform(String message) {
        if (transformInfo != null) {
            transformInfo.fail(message);
        }
        transitionTo(PaperState.FAILED);
    }

    // ========== 向量化阶段 ==========

    public void startEmbedding() {
        if (this.state != PaperState.TRANSFORMED) {
            return;
        }
        transitionTo(PaperState.EMBEDDING);
        this.registerEvent(new PaperEmbeddingStartedEvent(this));
    }

    public void addChunk(DocumentChunk chunk) {
        ensureState(PaperState.EMBEDDING, "Add chunk only during EMBEDDING");
        this.chunks.add(chunk);
    }

    public void completeEmbedding() {
        if (this.state != PaperState.EMBEDDING) {
            if (this.state == PaperState.EMBEDDING_COMPLETED) {
                return;
            }
            return;
        }
        if (chunks.isEmpty()) {
            throw new DomainException("Cannot complete embedding without chunks");
        }
        transitionTo(PaperState.EMBEDDING_COMPLETED);
        this.registerEvent(new PaperEmbeddingCompletedEvent(this));
    }

    public void failEmbedding(String message) {
        if (this.state != PaperState.EMBEDDING) {
            return;
        }
        transitionTo(PaperState.FAILED);
    }

    // ========== 材料提取 ==========

    public void startExtraction() {
        if (this.state != PaperState.EMBEDDING_COMPLETED) {
            return;
        }
        transitionTo(PaperState.EXTRACTING);
        this.registerEvent(new PaperExtractionStartedEvent(this));
    }

    public void completeExtraction(List<MaterialExtraction> materials) {
        if (this.state != PaperState.EXTRACTING) {
            if (this.state == PaperState.MATERIAL_EXTRACTED || this.state == PaperState.COMPLETED) {
                return;
            }
            return;
        }
        if (materials == null) {
            materials = List.of();
        }
        this.materials = new ArrayList<>(materials);
        transitionTo(PaperState.MATERIAL_EXTRACTED);
        this.registerEvent(new PaperMaterialExtractedEvent(this, materials));
    }

    public void failExtraction(String message) {
        if (this.state != PaperState.EXTRACTING) {
            return;
        }
        transitionTo(PaperState.FAILED);
    }

    public void complete() {
        if (this.state != PaperState.MATERIAL_EXTRACTED && this.state != PaperState.IRRELEVANT) {
            throw new IllegalStateTransitionException("Cannot complete process from state " + this.state);
        }
        transitionTo(PaperState.COMPLETED);
        this.registerEvent(new PaperCompletedEvent(this));
    }

    public void abandon(String reason) {
        ensureState(PaperState.FAILED, "Only failed papers can be abandoned");
        transitionTo(PaperState.ABANDONED);
        this.registerEvent(new PaperAbandonedEvent(this, reason));
    }

    private void transitionTo(PaperState target) {
        if (!PaperStateTransition.isValid(this.state, target)) {
            throw new IllegalStateTransitionException(
                "Cannot transition from " + this.state + " to " + target
            );
        }
        PaperState previous = this.state;
        this.state = target;
        this.registerEvent(new PaperStateChangedEvent(this, previous, target));
    }

    private void ensureState(PaperState expected, String message) {
        if (this.state != expected) {
            throw new IllegalStateTransitionException(message);
        }
    }

    public PaperSnapshot snapshot() {
        List<DocumentChunk> snapshotChunks = chunks == null ? List.of() : List.copyOf(chunks);
        List<MaterialExtraction> snapshotMaterials = materials == null ? List.of() : List.copyOf(materials);
        return new PaperSnapshot(
            getId(),
            title,
            authors,
            doi,
            yearPublished,
            paperSource,
            abstractContent,
            downloadUrl,
            citationCount,
            downloadInfo,
            transformInfo,
            relevanceInfo,
            snapshotChunks,
            snapshotMaterials,
            state
        );
    }

    public static Paper restore(PaperSnapshot snapshot) {
        Paper paper = new Paper();
        paper.setId(snapshot.id());
        paper.title = snapshot.title();
        paper.authors = snapshot.authors();
        paper.doi = snapshot.doi();
        paper.yearPublished = snapshot.yearPublished();
        paper.paperSource = snapshot.source();
        paper.abstractContent = snapshot.abstractContent();
        paper.downloadUrl = snapshot.downloadUrl();
        paper.citationCount = snapshot.citationCount();
        paper.downloadInfo = snapshot.downloadInfo();
        paper.transformInfo = snapshot.transformInfo();
        paper.relevanceInfo = snapshot.relevanceInfo();
        paper.chunks = new ArrayList<>(snapshot.chunks() == null ? List.of() : snapshot.chunks());
        paper.materials = new ArrayList<>(snapshot.materials() == null ? List.of() : snapshot.materials());
        paper.state = snapshot.state();
        return paper;
    }
}
