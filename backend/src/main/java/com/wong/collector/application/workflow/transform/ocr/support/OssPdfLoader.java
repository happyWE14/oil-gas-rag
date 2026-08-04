package com.wong.collector.application.workflow.transform.ocr.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 通过 x-file-storage 拉取 OSS 上的 PDF。
 * 避免直接对私有 OSS 用 URL.openStream 导致权限问题。
 */
@Component
@RequiredArgsConstructor
public class OssPdfLoader {

    private final FileStorageService fileStorageService;

    /**
     * 使用 fileStorageService 下载 OSS 上的文件。
     */
    public InputStream open(String url) {
        try {
            byte[] data = loadBytes(url);
            return new ByteArrayInputStream(data);
        } catch (Exception ex) {
            throw new IllegalStateException("读取 OSS PDF 失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 下载文件为字节数组，便于在内存中解析 PDF。
     */
    public byte[] loadBytes(String url) {
        try {
            return fileStorageService.download(url).bytes();
        } catch (Exception ex) {
            throw new IllegalStateException("读取 OSS PDF 失败: " + ex.getMessage(), ex);
        }
    }
}
