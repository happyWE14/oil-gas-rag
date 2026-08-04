package com.wong.collector.domain.paper.model;

import java.time.LocalDateTime;

import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 转换信息实体。
 */
@Getter
public class TransformInfo {

    private final TransformProvider provider;
    private TransformStatus status;
    private String markdownPath;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private TransformInfo(TransformProvider provider) {
        if (provider == null) {
            throw new DomainException("Transform provider required");
        }
        this.provider = provider;
        this.status = TransformStatus.PENDING;
    }

    public static TransformInfo init(TransformProvider provider) {
        return new TransformInfo(provider);
    }

    public static TransformInfo restore(TransformProvider provider, TransformStatus status,
                                        String markdownPath, String errorMessage,
                                        LocalDateTime startedAt, LocalDateTime completedAt) {
        TransformInfo info = new TransformInfo(provider);
        info.status = status;
        info.markdownPath = markdownPath;
        info.errorMessage = errorMessage;
        info.startedAt = startedAt;
        info.completedAt = completedAt;
        return info;
    }

    public void start(String markdownPath) {
        this.status = TransformStatus.PROCESSING;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
        this.markdownPath = markdownPath;
    }

    public void complete(String path) {
        this.status = TransformStatus.COMPLETED;
        this.markdownPath = path;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String error) {
        this.status = TransformStatus.FAILED;
        this.errorMessage = error;
    }

    public boolean isFailed() {
        return status == TransformStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == TransformStatus.COMPLETED;
    }
}
