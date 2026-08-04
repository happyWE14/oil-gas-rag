package com.wong.collector.domain.paper.model;

import java.time.LocalDateTime;

import com.wong.collector.domain.common.exception.DomainException;

import lombok.Getter;

/**
 * 下载信息实体。
 */
@Getter
public class DownloadInfo {

    private final String sourceUrl;
    private DownloadStatus status;
    private String ossUrl;
    private Long fileSize;
    private int tryTimes;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private DownloadInfo(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new DomainException("Download url required");
        }
        this.sourceUrl = sourceUrl;
        this.status = DownloadStatus.PENDING;
    }

    public static DownloadInfo create(String sourceUrl) {
        return new DownloadInfo(sourceUrl);
    }

    public static DownloadInfo restore(String sourceUrl, DownloadStatus status,
                                       String ossUrl, Long fileSize, int tryTimes,
                                       String errorMessage, LocalDateTime startedAt,
                                       LocalDateTime completedAt) {
        DownloadInfo info = new DownloadInfo(sourceUrl);
        info.status = status;
        info.ossUrl = ossUrl;
        info.fileSize = fileSize;
        info.tryTimes = tryTimes;
        info.errorMessage = errorMessage;
        info.startedAt = startedAt;
        info.completedAt = completedAt;
        return info;
    }

    public void startDownload() {
        this.status = DownloadStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
        this.errorMessage = null;
        this.tryTimes++;
    }

    public void complete(String ossUrl, Long size) {
        if (ossUrl == null || ossUrl.isBlank()) {
            throw new DomainException("OSS url required when completing download");
        }
        this.status = DownloadStatus.COMPLETED;
        this.ossUrl = ossUrl;
        this.fileSize = size;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void fail(String message) {
        this.status = DownloadStatus.FAILED;
        this.errorMessage = message;
    }

    public boolean isFailed() {
        return status == DownloadStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == DownloadStatus.COMPLETED;
    }
}
