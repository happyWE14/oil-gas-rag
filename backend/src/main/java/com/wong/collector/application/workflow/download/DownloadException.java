package com.wong.collector.application.workflow.download;

/**
 * 下载相关异常，默认可重试。
 */
public class DownloadException extends RuntimeException {
    public DownloadException(String message) {
        super(message);
    }

    public DownloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
